package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ClubMemberRow;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;
import java.util.Objects;

/**
 * Thành viên câu lạc bộ (SRS FR-CLUB-03, 04, 08; UC-08). Sinh viên gửi yêu cầu (PENDING); REJECTED hoặc LEFT thì
 * dùng lại dòng cũ (uq_club_members). Chủ nhiệm của CLB đó hoặc ADMIN duyệt (ACTIVE, joined_at) hoặc từ chối
 * (REJECTED), người gửi nhận thông báo; chủ nhiệm đổi vai trò thành viên ACTIVE giữa MEMBER và VICE_PRESIDENT.
 * Thành viên tự rời (LEFT), chủ nhiệm không rời được khi chưa chuyển giao; rời CLB thì các đăng ký hoạt động chưa
 * bắt đầu chuyển CANCELLED. Các thao tác đổi thành viên khóa dòng CLB trước.
 */
@Service
@RequiredArgsConstructor
public class ClubMembershipService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final ClubActivityParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ClubAccessPolicy accessPolicy;
    private final NotificationService notificationService;
    private final AuditService auditService;

    /** Gửi yêu cầu tham gia (chỉ sinh viên; vai trò khác → 403). */
    @Transactional
    public void join(AppUserPrincipal actor, Long clubId) {
        accessPolicy.requireStudent(actor);
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
        ClubMember existing = memberRepository.findByClubIdAndUserId(clubId, actor.getUserId()).orElse(null);
        ClubRule.requireCanJoin(club.getStatus(), existing);
        ClubMember member = existing;
        if (member == null) {
            member = new ClubMember();
            member.setClub(club);
            member.setUser(userRepository.getReferenceById(actor.getUserId()));
        }
        ClubRule.resetToPending(member);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ClubMemberRow> requests(AppUserPrincipal actor, Long clubId, ClubMemberStatus status, int page) {
        accessPolicy.requireManageMembers(actor, clubId);
        return memberRepository.findPageByClub(clubId, status, CatalogSupport.page(page)).map(ClubService::row);
    }

    @Transactional
    public void approve(AppUserPrincipal actor, Long clubId, Long memberId, String ipAddress) {
        ClubMember member = pending(actor, clubId, memberId);
        member.setStatus(ClubMemberStatus.ACTIVE);
        member.setMemberRole(ClubMemberRole.MEMBER);
        member.setJoinedAt(ClubRule.now());
        auditService.log(actor.getUserId(), AuditActions.APPROVE_CLUB_MEMBER, AuditActions.ENTITY_CLUB_MEMBER,
                memberId, "club=" + clubId + ", user=" + member.getUser().getId(), ipAddress);
        notify(actor, member, true);
    }

    @Transactional
    public void reject(AppUserPrincipal actor, Long clubId, Long memberId, String ipAddress) {
        ClubMember member = pending(actor, clubId, memberId);
        member.setStatus(ClubMemberStatus.REJECTED);
        auditService.log(actor.getUserId(), AuditActions.REJECT_CLUB_MEMBER, AuditActions.ENTITY_CLUB_MEMBER,
                memberId, "club=" + clubId + ", user=" + member.getUser().getId(), ipAddress);
        notify(actor, member, false);
    }

    /** Đổi vai trò thành viên ACTIVE giữa MEMBER và VICE_PRESIDENT; vai trò chủ nhiệm chỉ đổi qua chỉ định. */
    @Transactional
    public void changeRole(AppUserPrincipal actor, Long clubId, Long memberId, ClubMemberRole role,
                           String ipAddress) {
        accessPolicy.requireManageMembers(actor, clubId);
        lockClub(clubId);
        ClubMember member = inClub(clubId, memberId);
        if (member.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new BusinessException(ClubMessages.MEMBER_NOT_ACTIVE);
        }
        if (member.getMemberRole() == ClubMemberRole.PRESIDENT) {
            throw new BusinessException(ClubMessages.PRESIDENT_ROLE_LOCKED);
        }
        if (role != ClubMemberRole.MEMBER && role != ClubMemberRole.VICE_PRESIDENT) {
            throw new BusinessException(ClubMessages.ROLE_INVALID);
        }
        if (member.getMemberRole() == role) {
            return;
        }
        ClubMemberRole old = member.getMemberRole();
        member.setMemberRole(role);
        auditService.log(actor.getUserId(), AuditActions.CHANGE_CLUB_ROLE, AuditActions.ENTITY_CLUB_MEMBER,
                memberId, "club=" + clubId + ", " + old + " -> " + role, ipAddress);
    }

    /** Thành viên ACTIVE tự rời; trả số đăng ký hoạt động chưa bắt đầu đã bị hủy theo. */
    @Transactional
    public int leave(AppUserPrincipal actor, Long clubId, String ipAddress) {
        lockClub(clubId);
        ClubMember member = memberRepository.findByClubIdAndUserId(clubId, actor.getUserId())
                .filter(ClubAccessPolicy::isActive)
                .orElseThrow(() -> new BusinessException(ClubMessages.NOT_MEMBER));
        if (member.getMemberRole() == ClubMemberRole.PRESIDENT) {
            throw new BusinessException(ClubMessages.PRESIDENT_CANNOT_LEAVE);
        }
        member.setStatus(ClubMemberStatus.LEFT);
        member.setMemberRole(ClubMemberRole.MEMBER);
        int cancelled = participantRepository.cancelFutureRegistrations(clubId, actor.getUserId(), ClubRule.now());
        auditService.log(actor.getUserId(), AuditActions.LEAVE_CLUB, AuditActions.ENTITY_CLUB_MEMBER,
                member.getId(), "club=" + clubId + ", cancelledRegistrations=" + cancelled, ipAddress);
        return cancelled;
    }

    /** Yêu cầu đang chờ của CLB (đã kiểm tra quyền, đã khóa CLB, CLB phải đang hoạt động). */
    private ClubMember pending(AppUserPrincipal actor, Long clubId, Long memberId) {
        accessPolicy.requireManageMembers(actor, clubId);
        Club club = lockClub(clubId);
        if (club.getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ClubMessages.CLUB_INACTIVE);
        }
        ClubMember member = inClub(clubId, memberId);
        if (member.getStatus() != ClubMemberStatus.PENDING) {
            throw new BusinessException(ClubMessages.MEMBER_NOT_PENDING);
        }
        return member;
    }

    /** Dòng club_members của đúng CLB này; dòng của CLB khác hoặc không tồn tại → 403. */
    private ClubMember inClub(Long clubId, Long memberId) {
        return memberRepository.findById(memberId)
                .filter(m -> Objects.equals(m.getClub().getId(), clubId))
                .orElseThrow(() -> new AccessDeniedException("Member not in this club"));
    }

    private Club lockClub(Long clubId) {
        return clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
    }

    private void notify(AppUserPrincipal actor, ClubMember member, boolean approved) {
        String clubName = member.getClub().getName();
        Long userId = member.getUser().getId();
        notificationService.sendAutomatic(actor.getUserId(),
                (approved ? "Yêu cầu tham gia CLB đã được duyệt: " : "Yêu cầu tham gia CLB bị từ chối: ") + clubName,
                approved ? "Bạn đã trở thành thành viên của " + clubName + ". Bạn có thể đăng ký các hoạt động"
                        + " của câu lạc bộ." : "Yêu cầu tham gia " + clubName + " của bạn không được duyệt.",
                approved ? NotificationPriority.NORMAL : NotificationPriority.IMPORTANT, NotificationTargetType.USER,
                userId, List.of(userId));
    }
}
