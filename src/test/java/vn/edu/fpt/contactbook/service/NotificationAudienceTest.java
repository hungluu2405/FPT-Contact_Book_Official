package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.NotificationAudienceRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phạm vi gửi theo vai trò (kể cả targetId bị sửa) và người nhận theo từng phạm vi (loại trùng).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationAudienceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    /** users.id 3, giảng viên GV002 (teachers.id 2) dạy lớp 10. */
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    /** Trưởng bộ môn SE (id 1), không có hồ sơ giảng viên dạy lớp nào. */
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(15L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
            Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());

    @Mock NotificationAudienceRepository audienceRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SemesterRepository semesterRepository;

    private NotificationAudience audience;

    @BeforeEach
    void setUp() {
        audience = new NotificationAudience(audienceRepository, classSectionRepository, teacherRepository,
                semesterRepository, new UserAccessPolicy(teacherRepository));
        Department se = CatalogTestData.department(1L, "SE");
        Department ia = CatalogTestData.department(38L, "IA");
        Teacher gv002 = CatalogTestData.teacher(2L, "GV002", se);
        when(teacherRepository.findByUserId(3L)).thenReturn(Optional.of(gv002));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(CatalogTestData.section(10L, "SE-10",
                CatalogTestData.subject(1L, "PRJ301", se), CatalogTestData.semester(1L, "FA26"), gv002, 40,
                SectionStatus.OPEN)));
        when(classSectionRepository.findById(20L)).thenReturn(Optional.of(CatalogTestData.section(20L, "IA-20",
                CatalogTestData.subject(2L, "IAO201", ia), CatalogTestData.semester(1L, "FA26"),
                CatalogTestData.teacher(9L, "GV009", ia), 40, SectionStatus.OPEN)));
        when(audienceRepository.isStudentOfTeacher(anyLong(), anyLong())).thenReturn(false);
        when(audienceRepository.isStudentOfTeacher(4L, 2L)).thenReturn(true);
        when(audienceRepository.isParentOfTeacherStudent(5L, 2L)).thenReturn(true);
        when(audienceRepository.isTeacherOfDepartments(16L, Set.of(1L))).thenReturn(true);
    }

    @Test
    @DisplayName("Giảng viên: chỉ lớp mình dạy và SV/PH của lớp mình; lớp khác, bộ môn, toàn trường (sửa form) bị 403")
    void teacherScope() {
        assertThat(audience.allowedTypes(TEACHER))
                .containsExactlyInAnyOrder(NotificationTargetType.CLASS_SECTION, NotificationTargetType.USER);
        audience.requireCanSend(TEACHER, NotificationTargetType.CLASS_SECTION, 10L);
        audience.requireCanSend(TEACHER, NotificationTargetType.USER, 4L);
        audience.requireCanSend(TEACHER, NotificationTargetType.USER, 5L);

        assertThatThrownBy(() -> audience.requireCanSend(TEACHER, NotificationTargetType.CLASS_SECTION, 20L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(TEACHER, NotificationTargetType.CLASS_SECTION, 999L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(TEACHER, NotificationTargetType.USER, 18L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(TEACHER, NotificationTargetType.ALL, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(TEACHER, NotificationTargetType.DEPARTMENT, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Trưởng bộ môn: bộ môn mình, lớp thuộc bộ môn mình, GV bộ môn mình; bộ môn khác bị 403")
    void headScope() {
        assertThat(audience.allowedTypes(HEAD_SE)).containsExactlyInAnyOrder(NotificationTargetType.DEPARTMENT,
                NotificationTargetType.CLASS_SECTION, NotificationTargetType.USER);
        audience.requireCanSend(HEAD_SE, NotificationTargetType.DEPARTMENT, 1L);
        audience.requireCanSend(HEAD_SE, NotificationTargetType.CLASS_SECTION, 10L);
        audience.requireCanSend(HEAD_SE, NotificationTargetType.USER, 16L);

        assertThatThrownBy(() -> audience.requireCanSend(HEAD_SE, NotificationTargetType.DEPARTMENT, 38L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(HEAD_SE, NotificationTargetType.CLASS_SECTION, 20L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> audience.requireCanSend(HEAD_SE, NotificationTargetType.ROLE, 4L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ADMIN gửi mọi phạm vi; sinh viên không được gửi phạm vi nào")
    void adminAndStudent() {
        assertThat(audience.allowedTypes(ADMIN)).containsExactlyInAnyOrder(NotificationTargetType.values());
        audience.requireCanSend(ADMIN, NotificationTargetType.ALL, null);
        audience.requireCanSend(ADMIN, NotificationTargetType.CLASS_SECTION, 20L);

        assertThat(audience.allowedTypes(STUDENT)).isEmpty();
        assertThatThrownBy(() -> audience.requireCanSend(STUDENT, NotificationTargetType.USER, 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Người nhận lớp học phần = SV ENROLLED + PH liên kết, loại trùng (người vừa là SV vừa là PH)")
    void sectionRecipientsDeduplicated() {
        when(audienceRepository.studentsOfSection(10L)).thenReturn(List.of(4L, 18L));
        when(audienceRepository.parentsOfSection(10L)).thenReturn(List.of(5L, 18L, 5L));

        assertThat(audience.recipients(NotificationTargetType.CLASS_SECTION, 10L)).containsExactly(4L, 18L, 5L);
    }

    @Test
    @DisplayName("Người nhận theo từng phạm vi dùng đúng truy vấn")
    void recipientsPerType() {
        when(audienceRepository.allActive()).thenReturn(List.of(1L, 2L, 3L));
        when(audienceRepository.withRole(4L)).thenReturn(List.of(4L, 18L));
        when(audienceRepository.teachersOfDepartment(1L)).thenReturn(List.of(2L, 3L));
        when(audienceRepository.studentsOfClass(14L)).thenReturn(List.of(18L, 19L));
        when(audienceRepository.parentsOfClass(14L)).thenReturn(List.of(21L, 22L));
        when(audienceRepository.activeAmong(List.of(5L))).thenReturn(List.of(5L));
        when(audienceRepository.activeAmong(List.of(9L))).thenReturn(List.of());

        assertThat(audience.recipients(NotificationTargetType.ALL, null)).containsExactly(1L, 2L, 3L);
        assertThat(audience.recipients(NotificationTargetType.ROLE, 4L)).containsExactly(4L, 18L);
        assertThat(audience.recipients(NotificationTargetType.DEPARTMENT, 1L)).containsExactly(2L, 3L);
        assertThat(audience.recipients(NotificationTargetType.CLASS, 14L)).containsExactly(18L, 19L, 21L, 22L);
        assertThat(audience.recipients(NotificationTargetType.USER, 5L)).containsExactly(5L);
        assertThat(audience.recipients(NotificationTargetType.USER, 9L)).as("tài khoản bị khóa").isEmpty();
    }

    @Test
    @DisplayName("SV và PH của một nhóm sinh viên (thông báo tự động); danh sách rỗng không truy vấn")
    void studentsAndParents() {
        when(audienceRepository.studentUsers(anyCollection())).thenReturn(List.of(4L));
        when(audienceRepository.parentUsers(anyCollection())).thenReturn(List.of(5L, 4L));

        assertThat(audience.studentsAndParents(List.of(1L))).containsExactly(4L, 5L);
        assertThat(audience.studentsAndParents(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Ô chọn lớp học phần chỉ gồm học kỳ chưa kết thúc (end_date ≥ hôm nay), theo phạm vi người gửi")
    void sectionOptionsOnlyOpenSemesters() {
        LocalDate today = AppTime.today();
        when(semesterRepository.findByEndDateGreaterThanEqualOrderByStartDateDesc(today))
                .thenReturn(List.of(semester(1L, today), semester(38L, today.plusMonths(6))));

        audience.sections(ADMIN);
        audience.sections(TEACHER);
        audience.sections(HEAD_SE);

        verify(classSectionRepository).findForNotification(eq(List.of(1L, 38L)), eq(true), isNull(),
                eq(CatalogSupport.NO_IDS));
        verify(classSectionRepository).findForNotification(eq(List.of(1L, 38L)), eq(false), eq(2L),
                eq(CatalogSupport.NO_IDS));
        verify(classSectionRepository).findForNotification(eq(List.of(1L, 38L)), eq(false), isNull(),
                eq(Set.of(1L)));
    }

    @Test
    @DisplayName("Không còn học kỳ chưa kết thúc: dùng học kỳ mới nhất; chưa có học kỳ nào: rỗng, không truy vấn")
    void sectionOptionsFallback() {
        when(semesterRepository.findByEndDateGreaterThanEqualOrderByStartDateDesc(any())).thenReturn(List.of());
        when(semesterRepository.findAllByOrderByStartDateDesc())
                .thenReturn(List.of(semester(37L, LocalDate.of(2026, 8, 23)),
                        semester(5L, LocalDate.of(2025, 12, 20))));

        audience.sections(ADMIN);
        verify(classSectionRepository).findForNotification(eq(List.of(37L)), eq(true), isNull(), any());

        when(semesterRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of());
        assertThat(audience.sections(TEACHER)).isEmpty();
        verify(classSectionRepository, never()).findForNotification(any(), eq(false), any(), any());
    }

    @Test
    @DisplayName("Người không có phạm vi lớp học phần (SV): rỗng, không truy vấn")
    void sectionOptionsNoScope() {
        when(semesterRepository.findByEndDateGreaterThanEqualOrderByStartDateDesc(any()))
                .thenReturn(List.of(semester(1L, AppTime.today())));

        assertThat(audience.sections(STUDENT)).isEmpty();
        verify(classSectionRepository, never()).findForNotification(any(), anyBoolean(), any(), any());
    }

    private static Semester semester(Long id, LocalDate endDate) {
        Semester semester = CatalogTestData.semester(id, "S" + id);
        semester.setStartDate(endDate.minusMonths(4));
        semester.setEndDate(endDate);
        return semester;
    }
}
