package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.NotificationAudienceRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Phạm vi gửi thông báo (SRS FR-NOTI-01) và người nhận theo phạm vi. Quyền là hợp của mọi vai trò:
 * <ul>
 *   <li>ADMIN: mọi phạm vi.</li>
 *   <li>HEAD_OF_DEPARTMENT: DEPARTMENT là bộ môn mình; CLASS_SECTION có môn thuộc bộ môn mình; USER là giảng viên
 *       bộ môn mình, sinh viên ENROLLED ở lớp học phần thuộc bộ môn mình hoặc phụ huynh đã liên kết của họ.</li>
 *   <li>TEACHER: CLASS_SECTION mình dạy; USER là sinh viên ENROLLED ở lớp mình dạy hoặc phụ huynh của họ.</li>
 * </ul>
 * Người nhận chỉ gồm tài khoản is_active = 1, đã loại trùng (LinkedHashSet); loại người gửi ở NotificationService.
 */
@Component
@RequiredArgsConstructor
public class NotificationAudience {

    private final NotificationAudienceRepository audienceRepository;
    private final ClassSectionRepository classSectionRepository;
    private final TeacherRepository teacherRepository;
    private final SemesterRepository semesterRepository;
    private final UserAccessPolicy userAccessPolicy;

    /** Phạm vi người gửi được chọn trên form; rỗng là không được gửi thông báo. */
    public Set<NotificationTargetType> allowedTypes(AppUserPrincipal actor) {
        if (userAccessPolicy.isAdmin(actor)) {
            return EnumSet.allOf(NotificationTargetType.class);
        }
        Set<NotificationTargetType> types = EnumSet.noneOf(NotificationTargetType.class);
        if (!headDepartments(actor).isEmpty()) {
            types.addAll(List.of(NotificationTargetType.DEPARTMENT, NotificationTargetType.CLASS_SECTION,
                    NotificationTargetType.USER));
        }
        if (teacher(actor).isPresent()) {
            types.addAll(List.of(NotificationTargetType.CLASS_SECTION, NotificationTargetType.USER));
        }
        return types;
    }

    /** Kiểm tra lại phạm vi gửi ở service (không tin targetId từ form); ngoài phạm vi → 403. */
    public void requireCanSend(AppUserPrincipal actor, NotificationTargetType type, Long targetId) {
        if (!canSend(actor, type, targetId)) {
            throw new AccessDeniedException("Notification target outside sender scope");
        }
    }

    boolean canSend(AppUserPrincipal actor, NotificationTargetType type, Long targetId) {
        if (userAccessPolicy.isAdmin(actor)) {
            return true;
        }
        if (targetId == null) {
            return false;
        }
        Set<Long> departments = headDepartments(actor);
        Optional<Teacher> teacher = teacher(actor);
        return switch (type) {
            case DEPARTMENT -> departments.contains(targetId);
            case CLASS_SECTION -> classSectionRepository.findById(targetId)
                    .map(section -> departments.contains(section.getSubject().getDepartment().getId())
                            || teacher.map(t -> Objects.equals(section.getTeacher().getId(), t.getId())).orElse(false))
                    .orElse(false);
            case USER -> (!departments.isEmpty()
                    && (audienceRepository.isTeacherOfDepartments(targetId, departments)
                    || audienceRepository.isStudentOfDepartments(targetId, departments)
                    || audienceRepository.isParentOfDepartmentStudent(targetId, departments)))
                    || teacher.map(t -> audienceRepository.isStudentOfTeacher(targetId, t.getId())
                    || audienceRepository.isParentOfTeacherStudent(targetId, t.getId())).orElse(false);
            case ALL, ROLE, CLASS -> false;
        };
    }

    /**
     * Lớp học phần cho ô chọn trên form gửi (ADMIN mọi lớp, TBM lớp bộ môn mình, GV lớp mình dạy), mới nhất trước.
     * Chỉ lớp của học kỳ chưa kết thúc (end_date ≥ hôm nay); không còn học kỳ nào như vậy thì học kỳ mới nhất. Đây chỉ
     * là thu gọn lựa chọn: khi gửi, phạm vi vẫn kiểm tra bằng canSend như trước.
     */
    public List<ClassSection> sections(AppUserPrincipal actor) {
        boolean admin = userAccessPolicy.isAdmin(actor);
        Set<Long> departments = headDepartments(actor);
        Long teacherId = admin ? null : teacher(actor).map(Teacher::getId).orElse(null);
        if (!admin && departments.isEmpty() && teacherId == null) {
            return List.of();
        }
        List<Long> semesterIds = optionSemesterIds();
        if (semesterIds.isEmpty()) {
            return List.of();
        }
        // Danh sách IN không được rỗng (như ClassSectionService.list).
        return classSectionRepository.findForNotification(semesterIds, admin, teacherId,
                departments.isEmpty() ? CatalogSupport.NO_IDS : departments);
    }

    private List<Long> optionSemesterIds() {
        List<Semester> open = semesterRepository.findByEndDateGreaterThanEqualOrderByStartDateDesc(AppTime.today());
        if (!open.isEmpty()) {
            return open.stream().map(Semester::getId).toList();
        }
        return semesterRepository.findAllByOrderByStartDateDesc().stream().findFirst()
                .map(semester -> List.of(semester.getId())).orElse(List.of());
    }

    public Set<Long> headDepartments(AppUserPrincipal actor) {
        return actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT) ? userAccessPolicy.headDepartmentIds(actor) : Set.of();
    }

    /** Người nhận của một phạm vi (chưa loại người gửi). */
    public Set<Long> recipients(NotificationTargetType type, Long targetId) {
        Set<Long> users = new LinkedHashSet<>();
        switch (type) {
            case ALL -> users.addAll(audienceRepository.allActive());
            case ROLE -> users.addAll(audienceRepository.withRole(targetId));
            case DEPARTMENT -> users.addAll(audienceRepository.teachersOfDepartment(targetId));
            case CLASS -> {
                users.addAll(audienceRepository.studentsOfClass(targetId));
                users.addAll(audienceRepository.parentsOfClass(targetId));
            }
            case CLASS_SECTION -> {
                users.addAll(audienceRepository.studentsOfSection(targetId));
                users.addAll(audienceRepository.parentsOfSection(targetId));
            }
            case USER -> users.addAll(audienceRepository.activeAmong(List.of(targetId)));
        }
        return users;
    }

    /** Tài khoản của các sinh viên (students.id) cùng phụ huynh đã liên kết (thông báo tự động). */
    public Set<Long> studentsAndParents(Collection<Long> studentIds) {
        Set<Long> users = new LinkedHashSet<>();
        if (!studentIds.isEmpty()) {
            users.addAll(audienceRepository.studentUsers(studentIds));
            users.addAll(audienceRepository.parentUsers(studentIds));
        }
        return users;
    }

    private Optional<Teacher> teacher(AppUserPrincipal actor) {
        return actor.hasRole(RoleCodes.TEACHER) ? teacherRepository.findByUserId(actor.getUserId()) : Optional.empty();
    }
}
