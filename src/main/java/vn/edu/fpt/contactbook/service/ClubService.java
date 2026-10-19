package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.dto.ClubDetail;
import vn.edu.fpt.contactbook.dto.ClubFilter;
import vn.edu.fpt.contactbook.dto.ClubForm;
import vn.edu.fpt.contactbook.dto.ClubMemberRow;
import vn.edu.fpt.contactbook.dto.ClubView;
import vn.edu.fpt.contactbook.dto.MyClubRow;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Câu lạc bộ (SRS FR-CLUB-01, 02): ADMIN thêm, sửa, ngừng hoạt động, mở lại, chỉ định / chuyển giao chủ nhiệm;
 * không có chức năng xóa. Mọi vai trò xem danh sách CLB ACTIVE và chi tiết CLB; sinh viên xem "CLB của tôi".
 * Chỉ định chủ nhiệm khóa dòng CLB rồi đổi chủ nhiệm cũ thành MEMBER và người mới thành PRESIDENT trong cùng
 * transaction, nên mỗi CLB có đúng một PRESIDENT ACTIVE.
 */
@Service
@RequiredArgsConstructor
public class ClubService {

    static final List<String> TEACHER_ROLES = List.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT);

    private final ClubRepository clubRepository;
    private final ClubMemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClubLogoStorage logoStorage;
    private final ClubAccessPolicy accessPolicy;
    private final UserAccessPolicy userAccessPolicy;
    private final AuditService auditService;

    // ---------- ADMIN ----------

    @Transactional(readOnly = true)
    public Page<ClubView> list(AppUserPrincipal actor, ClubFilter filter, int page) {
        userAccessPolicy.requireAdmin(actor);
        return views(clubRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getStatus(),
                CatalogSupport.page(page)));
    }

    @Transactional(readOnly = true)
    public ClubView detail(AppUserPrincipal actor, Long id) {
        userAccessPolicy.requireAdmin(actor);
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public ClubForm editForm(AppUserPrincipal actor, Long id) {
        userAccessPolicy.requireAdmin(actor);
        Club club = find(id);
        ClubForm form = new ClubForm();
        form.setCode(club.getCode());
        form.setName(club.getName());
        form.setDescription(club.getDescription());
        form.setFoundedDate(club.getFoundedDate());
        form.setAdvisorUserId(club.getAdvisor() == null ? null : club.getAdvisor().getId());
        return form;
    }

    /** Giảng viên phụ trách chọn được: người có vai trò TEACHER hoặc HEAD_OF_DEPARTMENT đang hoạt động. */
    @Transactional(readOnly = true)
    public List<OptionView> advisors() {
        return userRoleRepository.findActiveUsersWithRole(TEACHER_ROLES).stream()
                .map(u -> new OptionView(u.getId(), u.getPhone(), u.getFullName(), null)).toList();
    }

    @Transactional
    public Long create(AppUserPrincipal actor, ClubForm form, AvatarUpload logo, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        Club club = apply(new Club(), form);
        replaceLogo(club, logo);
        save(club);
        auditService.log(actor.getUserId(), AuditActions.CREATE_CLUB, AuditActions.ENTITY_CLUB, club.getId(),
                "code=" + club.getCode(), ipAddress);
        return club.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, ClubForm form, AvatarUpload logo, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        Club club = apply(find(id), form);
        replaceLogo(club, logo);
        save(club);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_CLUB, AuditActions.ENTITY_CLUB, id,
                "code=" + club.getCode() + (logo == null ? "" : ", logo"), ipAddress);
    }

    @Transactional
    public void deactivate(AppUserPrincipal actor, Long id, String ipAddress) {
        changeStatus(actor, id, ClubStatus.INACTIVE, ipAddress);
    }

    @Transactional
    public void activate(AppUserPrincipal actor, Long id, String ipAddress) {
        changeStatus(actor, id, ClubStatus.ACTIVE, ipAddress);
    }

    /**
     * Chỉ định hoặc chuyển giao chủ nhiệm (theo mã sinh viên). Người mới phải có vai trò STUDENT và đang hoạt động;
     * đã có dòng club_members (bất kỳ trạng thái) thì cập nhật dòng đó (uq_club_members), chưa có thì tạo. Chủ nhiệm
     * cũ thành MEMBER, vẫn ACTIVE. Làm được cả khi CLB ngừng hoạt động.
     */
    @Transactional
    public void assignPresident(AppUserPrincipal actor, Long clubId, String studentCode, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        Club club = clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
        String code = CatalogSupport.normalizeCode(studentCode);
        if (code == null) {
            throw new BusinessException(ClubMessages.STUDENT_CODE_REQUIRED, "studentCode");
        }
        User user = studentRepository.findByStudentCode(code).map(s -> s.getUser())
                .orElseThrow(() -> new BusinessException(ClubMessages.STUDENT_NOT_FOUND, "studentCode"));
        if (!userRoleRepository.isActiveWithRole(user.getId(), List.of(RoleCodes.STUDENT))) {
            throw new BusinessException(ClubMessages.PRESIDENT_NOT_STUDENT, "studentCode");
        }
        List<ClubMember> presidents = memberRepository.findPresidents(clubId);
        if (presidents.stream().anyMatch(p -> Objects.equals(p.getUser().getId(), user.getId()))) {
            throw new BusinessException(ClubMessages.ALREADY_PRESIDENT, "studentCode");
        }
        presidents.forEach(p -> p.setMemberRole(ClubMemberRole.MEMBER));
        ClubMember member = memberRepository.findByClubIdAndUserId(clubId, user.getId()).orElseGet(() -> {
            ClubMember created = new ClubMember();
            created.setClub(club);
            created.setUser(user);
            return created;
        });
        if (member.getStatus() != ClubMemberStatus.ACTIVE || member.getJoinedAt() == null) {
            member.setJoinedAt(ClubRule.now());
        }
        member.setStatus(ClubMemberStatus.ACTIVE);
        member.setMemberRole(ClubMemberRole.PRESIDENT);
        memberRepository.saveAndFlush(member);
        String previous = presidents.stream().map(p -> p.getUser().getId().toString())
                .collect(Collectors.joining(","));
        auditService.log(actor.getUserId(), AuditActions.ASSIGN_CLUB_PRESIDENT, AuditActions.ENTITY_CLUB, clubId,
                "from=" + (previous.isEmpty() ? "none" : previous) + ", to=" + user.getId(), ipAddress);
    }

    // ---------- Mọi vai trò ----------

    /** Danh sách CLB đang hoạt động. */
    @Transactional(readOnly = true)
    public Page<ClubView> activeClubs(String keyword, int page) {
        return views(clubRepository.search(LikePatterns.containsIgnoreCase(keyword), ClubStatus.ACTIVE,
                CatalogSupport.page(page)));
    }

    /** Chi tiết CLB và quan hệ của người xem với CLB; CLB ngừng hoạt động vẫn xem được (có nhãn). */
    @Transactional(readOnly = true)
    public ClubDetail view(AppUserPrincipal actor, Long clubId) {
        Club club = find(clubId);
        ClubMember mine = accessPolicy.membership(actor, clubId).orElse(null);
        ClubMemberStatus myStatus = mine == null ? null : mine.getStatus();
        boolean canJoin = actor.hasRole(RoleCodes.STUDENT) && club.getStatus() == ClubStatus.ACTIVE
                && (myStatus == null || myStatus == ClubMemberStatus.REJECTED || myStatus == ClubMemberStatus.LEFT);
        boolean canLeave = ClubAccessPolicy.isActive(mine) && !ClubAccessPolicy.isPresident(mine);
        return new ClubDetail(view(club), myStatus, mine == null ? null : mine.getMemberRole(), canJoin, canLeave,
                accessPolicy.canManageMembers(actor, mine), accessPolicy.canManageActivities(actor, mine));
    }

    /** Thành viên ACTIVE (tên và vai trò trong CLB), chủ nhiệm trước. */
    @Transactional(readOnly = true)
    public Page<ClubMemberRow> members(Long clubId, int page) {
        find(clubId);
        return memberRepository.findPageByClub(clubId, ClubMemberStatus.ACTIVE, CatalogSupport.page(page))
                .map(ClubService::row);
    }

    /** "CLB của tôi" (chỉ sinh viên): đang chờ duyệt, đang là thành viên, bị từ chối. */
    @Transactional(readOnly = true)
    public List<MyClubRow> myClubs(AppUserPrincipal actor) {
        accessPolicy.requireStudent(actor);
        return memberRepository.findByUser(actor.getUserId(), List.of(ClubMemberStatus.PENDING,
                        ClubMemberStatus.ACTIVE, ClubMemberStatus.REJECTED)).stream()
                .map(m -> new MyClubRow(m.getClub().getId(), m.getClub().getCode(), m.getClub().getName(),
                        m.getClub().getStatus(), m.getMemberRole(), m.getStatus()))
                .toList();
    }

    static ClubMemberRow row(ClubMember m) {
        return new ClubMemberRow(m.getId(), m.getUser().getId(), m.getUser().getFullName(), m.getMemberRole(),
                m.getStatus(), m.getJoinedAt(), m.getCreatedAt());
    }

    private void changeStatus(AppUserPrincipal actor, Long id, ClubStatus status, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        Club club = clubRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
        if (club.getStatus() == status) {
            throw new BusinessException(status == ClubStatus.ACTIVE ? ClubMessages.ALREADY_ACTIVE
                    : ClubMessages.ALREADY_INACTIVE);
        }
        club.setStatus(status);
        auditService.log(actor.getUserId(), status == ClubStatus.ACTIVE ? AuditActions.ACTIVATE_CLUB
                : AuditActions.DEACTIVATE_CLUB, AuditActions.ENTITY_CLUB, id, "code=" + club.getCode(), ipAddress);
    }

    private Club apply(Club club, ClubForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 150, "name", ClubMessages.NAME_REQUIRED,
                ClubMessages.NAME_SIZE, errors);
        String description = CatalogSupport.optionalText(form.getDescription(), ClubRule.MAX_DESCRIPTION,
                "description", ClubMessages.DESCRIPTION_SIZE, errors);
        if (form.getFoundedDate() != null && form.getFoundedDate().isAfter(AppTime.today())) {
            errors.put("foundedDate", ClubMessages.FOUNDED_IN_FUTURE);
        }
        Long advisorId = form.getAdvisorUserId();
        Long currentAdvisor = club.getAdvisor() == null ? null : club.getAdvisor().getId();
        // Giữ nguyên giảng viên phụ trách cũ thì không kiểm tra lại (người đó có thể đã bị khóa).
        if (advisorId != null && !advisorId.equals(currentAdvisor)
                && !userRoleRepository.isActiveWithRole(advisorId, TEACHER_ROLES)) {
            errors.put("advisorUserId", ClubMessages.ADVISOR_INVALID);
        }
        if (code != null && (club.getId() == null ? clubRepository.existsByCode(code)
                : clubRepository.existsByCodeAndIdNot(code, club.getId()))) {
            errors.putIfAbsent("code", ClubMessages.CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        club.setCode(code);
        club.setName(name);
        club.setDescription(description);
        club.setFoundedDate(form.getFoundedDate());
        club.setAdvisor(advisorId == null ? null : userRepository.getReferenceById(advisorId));
        return club;
    }

    /**
     * Lưu logo mới (nếu có) trước khi ghi database; transaction rollback thì xóa logo mới, commit thì xóa logo cũ.
     */
    private void replaceLogo(Club club, AvatarUpload logo) {
        if (logo == null) {
            return;
        }
        String newFile = logoStorage.store(logo);
        Optional<String> oldFile = ClubLogoStorage.fileNameOf(club.getLogoUrl());
        club.setLogoUrl(ClubLogoStorage.URL_PREFIX + newFile);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        oldFile.ifPresent(logoStorage::delete);
                    } else {
                        logoStorage.delete(newFile);
                    }
                }
            });
        }
    }

    private void save(Club club) {
        try {
            clubRepository.saveAndFlush(club);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Club find(Long id) {
        return clubRepository.findWithAdvisor(id).orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
    }

    private ClubView view(Club club) {
        return views(List.of(club)).getFirst();
    }

    private Page<ClubView> views(Page<Club> clubs) {
        List<ClubView> content = views(clubs.getContent());
        Map<Long, ClubView> byId = content.stream().collect(Collectors.toMap(ClubView::id, v -> v));
        return clubs.map(c -> byId.get(c.getId()));
    }

    /** Số thành viên và chủ nhiệm của cả danh sách, mỗi loại một truy vấn. */
    private List<ClubView> views(List<Club> clubs) {
        if (clubs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = clubs.stream().map(Club::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : memberRepository.countActiveByClubs(ids)) {
            counts.put((Long) r[0], ((Number) r[1]).longValue());
        }
        Map<Long, String> presidents = memberRepository.findPresidentsOf(ids).stream()
                .collect(Collectors.toMap(m -> m.getClub().getId(), m -> m.getUser().getFullName(), (a, b) -> a));
        return clubs.stream().map(c -> new ClubView(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                c.getLogoUrl(), c.getFoundedDate(), c.getAdvisor() == null ? null : c.getAdvisor().getId(),
                c.getAdvisor() == null ? null : c.getAdvisor().getFullName(), c.getStatus(),
                counts.getOrDefault(c.getId(), 0L), presidents.get(c.getId()))).toList();
    }
}
