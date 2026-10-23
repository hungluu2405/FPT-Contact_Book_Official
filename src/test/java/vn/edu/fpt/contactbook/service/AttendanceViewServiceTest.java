package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xem điểm danh: phạm vi sinh viên (của mình), phụ huynh (con đã liên kết), giảng viên (lớp mình), trưởng bộ môn
 * (bộ môn mình); tổng hợp theo môn kèm cảnh báo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceViewServiceTest {

    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal PARENT = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());
    static final AppUserPrincipal OTHER_TEACHER = CatalogTestData.principal(16L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal HEAD_IA = CatalogTestData.principal(15L,
            Set.of(RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(38L)));

    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock StudentRepository studentRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock ExamRepository examRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private AttendanceViewService service;
    private ClassSection section;
    private Student hs260001;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        UserAccessPolicy userAccessPolicy = new UserAccessPolicy(teacherRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(userAccessPolicy);
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        ScheduleViewService scheduleViewService = new ScheduleViewService(semesterRepository, teacherRepository,
                studentRepository, parentRepository, parentStudentRepository, classSectionRepository,
                enrollmentRepository, classSessionRepository, examRepository);
        service = new AttendanceViewService(new AttendanceScope(teacherRepository, classSectionRepository,
                classSectionService, userAccessPolicy), new AttendanceRule(20), scheduleViewService,
                teacherRepository, classSectionRepository, classSessionRepository,
                enrollmentRepository, attendanceRepository);

        Department se = CatalogTestData.department(1L, "SE");
        Semester hk1 = CatalogTestData.semester(1L, "HK1-2627");
        hk1.setStartDate(LocalDate.of(2026, 9, 7));
        hk1.setEndDate(LocalDate.of(2026, 12, 20));
        hk1.setCurrent(true);
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se), hk1,
                CatalogTestData.teacher(2L, "GV002", se), 40, SectionStatus.OPEN);
        hs260001 = CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING);

        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(hk1));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(studentRepository.findByUserId(4L)).thenReturn(Optional.of(hs260001));
        Parent parent = new Parent();
        parent.setId(1L);
        when(parentRepository.findByUserId(5L)).thenReturn(Optional.of(parent));
        when(parentStudentRepository.existsByIdParentIdAndIdStudentId(1L, 1L)).thenReturn(true);
        when(enrollmentRepository.findAttendedInSemester(1L, 1L)).thenReturn(List.of(
                CatalogTestData.enrollment(1L, section, hs260001, EnrollmentStatus.ENROLLED)));
        // 10 buổi chưa hủy; HS260001: 7 có mặt, 1 vắng có phép, 2 vắng không phép → vắng 30%.
        when(classSessionRepository.countActiveBySections(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 10L}));
        when(attendanceRepository.countBySectionAndStatus(anyLong(), anyCollection())).thenReturn(List.of(
                new Object[]{10L, AttendanceStatus.PRESENT, 7L},
                new Object[]{10L, AttendanceStatus.ABSENT_EXCUSED, 1L},
                new Object[]{10L, AttendanceStatus.ABSENT_UNEXCUSED, 2L}));
    }

    @Test
    @DisplayName("Học sinh xem điểm danh của chính mình theo môn, có tỷ lệ và cảnh báo")
    void studentSeesOwnSummary() {
        List<StudentAttendanceRow> rows = service.student(STUDENT, Audience.STUDENT, 999L, null).data();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.subjectCode()).isEqualTo("TOAN10");
            assertThat(row.summary().absent()).isEqualTo(3);
            assertThat(row.summary().absentRate()).isEqualByComparingTo("30.0");
            assertThat(row.summary().attendanceRate()).isEqualByComparingTo("70.0");
            assertThat(row.summary().warning()).isTrue();
        });
    }

    @Test
    @DisplayName("Học sinh: studentId trong request bị bỏ qua, luôn là hồ sơ của chính mình")
    void studentIdFromRequestIsIgnored() {
        service.student(STUDENT, Audience.STUDENT, 2L, null);

        verify(enrollmentRepository).findAttendedInSemester(1L, 1L);
    }

    @Test
    @DisplayName("Người không có hồ sơ học sinh bị 403")
    void noStudentProfile() {
        AppUserPrincipal other = CatalogTestData.principal(9L, Set.of(RoleCodes.STUDENT), Map.of());

        assertThatThrownBy(() -> service.student(other, Audience.STUDENT, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Phụ huynh xem được con đã liên kết")
    void parentSeesLinkedChild() {
        assertThat(service.student(PARENT, Audience.PARENT, 1L, null).data()).hasSize(1);
    }

    @Test
    @DisplayName("Phụ huynh xem con chưa liên kết → 403, cả trang tổng hợp và lịch sử")
    void parentDeniedForUnlinkedChild() {
        assertThatThrownBy(() -> service.student(PARENT, Audience.PARENT, 2L, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.history(PARENT, Audience.PARENT, 2L, 10L, 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Lịch sử môn mà học sinh không học hoặc đã rút → 403")
    void historyOfOtherSectionIsDenied() {
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(STUDENT, Audience.STUDENT, null, 10L, 0))
                .isInstanceOf(AccessDeniedException.class);

        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(
                CatalogTestData.enrollment(1L, section, hs260001, EnrollmentStatus.DROPPED)));
        assertThatThrownBy(() -> service.history(STUDENT, Audience.STUDENT, null, 10L, 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Lịch sử môn đang học: tổng hợp cùng công thức với trang theo môn")
    void historyOfOwnSection() {
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(
                CatalogTestData.enrollment(1L, section, hs260001, EnrollmentStatus.ENROLLED)));
        when(classSessionRepository.findByClassSectionIdOrderBySessionDateAscStartTimeAsc(anyLong(), any()))
                .thenReturn(Page.empty());
        when(classSessionRepository.countByClassSectionIdAndStatusNot(10L, SessionStatus.CANCELLED)).thenReturn(10L);

        assertThat(service.history(STUDENT, Audience.STUDENT, null, 10L, 0).summary().summary().warning()).isTrue();
    }

    @Test
    @DisplayName("Giáo viên khác xem thống kê lớp không phải của mình → 403")
    void otherTeacherDeniedStats() {
        when(teacherRepository.findByUserId(16L)).thenReturn(Optional.of(
                CatalogTestData.teacher(8L, "GV003", section.getSubject().getDepartment())));

        assertThatThrownBy(() -> service.section(OTHER_TEACHER, Access.TEACHER, 10L, 0, 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tổ trưởng chuyên môn khác xem thống kê lớp ngoài tổ chuyên môn → 403")
    void headOfOtherDepartmentDeniedStats() {
        assertThatThrownBy(() -> service.section(HEAD_IA, Access.MANAGER, 10L, 0, 0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Người chưa có hồ sơ giáo viên thấy danh sách lớp giảng dạy rỗng")
    void teacherSectionsWithoutProfile() {
        Page<TeacherSectionView> page = service.teacherSections(HEAD_IA, null, 0).data();

        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("Danh sách lớp giảng dạy theo học kỳ hiện tại")
    void teacherSectionsOfCurrentSemester() {
        AppUserPrincipal teacher = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
        when(teacherRepository.findByUserId(3L)).thenReturn(Optional.of(section.getTeacher()));
        when(classSectionRepository.findByTeacherIdAndSemesterIdOrderByCodeAsc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(section)));

        assertThat(service.teacherSections(teacher, null, 0).data())
                .extracting(TeacherSectionView::code).containsExactly("TOAN10-HK1-2627-01");
        verify(classSectionRepository)
                .findByTeacherIdAndSemesterIdOrderByCodeAsc(eq(2L), eq(1L), any());
    }
}
