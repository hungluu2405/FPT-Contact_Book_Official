package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Optional;

/**
 * Quyền trong một câu lạc bộ (vai trò CLB lưu ở club_members.member_role, tách biệt vai trò hệ thống):
 * duyệt và đổi vai trò thành viên là chủ nhiệm ACTIVE của CLB đó hoặc ADMIN; quản lý hoạt động và ghi nhận tham dự
 * là chủ nhiệm hoặc phó chủ nhiệm ACTIVE của CLB đó hoặc ADMIN. Không đủ quyền → 403, kiểm tra trước cả khi biết CLB
 * có tồn tại để không dò được id. Giảng viên phụ trách chỉ xem.
 */
@Component
@RequiredArgsConstructor
public class ClubAccessPolicy {

    private final ClubMemberRepository memberRepository;

    public boolean isAdmin(AppUserPrincipal actor) {
        return actor.hasRole(RoleCodes.ADMIN);
    }

    public Optional<ClubMember> membership(AppUserPrincipal actor, Long clubId) {
        return memberRepository.findByClubIdAndUserId(clubId, actor.getUserId());
    }

    static boolean isActive(ClubMember member) {
        return member != null && member.getStatus() == ClubMemberStatus.ACTIVE;
    }

    static boolean isPresident(ClubMember member) {
        return isActive(member) && member.getMemberRole() == ClubMemberRole.PRESIDENT;
    }

    static boolean isOfficer(ClubMember member) {
        return isPresident(member)
                || (isActive(member) && member.getMemberRole() == ClubMemberRole.VICE_PRESIDENT);
    }

    public boolean canManageMembers(AppUserPrincipal actor, ClubMember membership) {
        return isAdmin(actor) || isPresident(membership);
    }

    public boolean canManageActivities(AppUserPrincipal actor, ClubMember membership) {
        return isAdmin(actor) || isOfficer(membership);
    }

    public void requireManageMembers(AppUserPrincipal actor, Long clubId) {
        if (!canManageMembers(actor, isAdmin(actor) ? null : membership(actor, clubId).orElse(null))) {
            throw new AccessDeniedException("Not the president of this club");
        }
    }

    public void requireManageActivities(AppUserPrincipal actor, Long clubId) {
        if (!canManageActivities(actor, isAdmin(actor) ? null : membership(actor, clubId).orElse(null))) {
            throw new AccessDeniedException("Not an officer of this club");
        }
    }

    public void requireStudent(AppUserPrincipal actor) {
        if (!actor.hasRole(RoleCodes.STUDENT)) {
            throw new AccessDeniedException("Only students join clubs");
        }
    }
}
