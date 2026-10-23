package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.EnrollClassResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ghi danh: trùng, vượt sĩ số, sĩ số NULL, lớp không OPEN, SV không STUDYING, ghi danh lại SV DROPPED,
 * gỡ, ghi danh theo lớp hành chính, chỉ ADMIN, khóa dòng lớp trước khi đếm.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock StudentRepository studentRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private EnrollmentService service;
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
        service = new EnrollmentService(classSectionRepository, enrollmentRepository, studentRepository,
                schoolClassRepository, classSectionService, userAccessPolicy, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se),
                CatalogTestData.semester(1L, "HK1-2627"), CatalogTestData.teacher(2L, "GV002", se), 2, SectionStatus.OPEN);
        hs260001 = CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING);
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(hs260001));
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Ghi danh HS mới: khóa dòng lớp trước rồi mới đếm ENROLLED, thêm dòng ENROLLED, ghi audit")
    void enrollNewStudent() {
        enrolledCount(1);

        boolean reEnrolled = service.enroll(ADMIN, 10L, 1L, "127.0.0.1");

        assertThat(reEnrolled).isFalse();
        ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(saved.getValue().getClassSection()).isSameAs(section);
        InOrder order = inOrder(classSectionRepository, enrollmentRepository);
        order.verify(classSectionRepository).findByIdForUpdate(10L);
        order.verify(enrollmentRepository).countByClassSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED);
        verify(auditLogRepository).save(any());
    }

    @Test
    void duplicateEnrollmentRejected() {
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(
                CatalogTestData.enrollment(5L, section, hs260001, EnrollmentStatus.ENROLLED)));

        assertThatThrownBy(() -> service.enroll(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.ALREADY_ENROLLED);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void completedStudentRejected() {
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(
                CatalogTestData.enrollment(5L, section, hs260001, EnrollmentStatus.COMPLETED)));

        assertThatThrownBy(() -> service.enroll(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("Lớp đã đủ sĩ số (2/2) → từ chối với số liệu cụ thể")
    void fullSectionRejected() {
        enrolledCount(2);

        assertThatThrownBy(() -> service.enroll(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage("Lớp môn học đã đủ sĩ số (2/2)");
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sĩ số NULL là không giới hạn")
    void unlimitedSection() {
        section.setMaxStudents(null);
        enrolledCount(5000);

        service.enroll(ADMIN, 10L, 1L, "127.0.0.1");

        verify(enrollmentRepository).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SectionStatus.class, names = {"CLOSED", "CANCELLED"})
    void sectionNotOpenRejected(SectionStatus status) {
        section.setStatus(status);

        assertThatThrownBy(() -> service.enroll(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.SECTION_NOT_OPEN);
        assertThatThrownBy(() -> service.enrollClass(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.SECTION_NOT_OPEN);
    }

    @ParameterizedTest
    @EnumSource(value = StudentStatus.class, names = {"ON_LEAVE", "GRADUATED", "DROPPED"})
    void studentNotStudyingRejected(StudentStatus status) {
        hs260001.setStatus(status);

        assertThatThrownBy(() -> service.enroll(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.STUDENT_NOT_STUDYING);
    }

    @Test
    @DisplayName("HS đã DROPPED: đổi chính dòng cũ về ENROLLED, cập nhật thời điểm, không thêm dòng mới")
    void reEnrollDroppedStudent() {
        Enrollment dropped = CatalogTestData.enrollment(5L, section, hs260001, EnrollmentStatus.DROPPED);
        LocalDateTime before = dropped.getEnrolledAt();
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(dropped));
        enrolledCount(1);

        boolean reEnrolled = service.enroll(ADMIN, 10L, 1L, "127.0.0.1");

        assertThat(reEnrolled).isTrue();
        assertThat(dropped.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(dropped.getEnrolledAt()).isAfter(before);
        verify(enrollmentRepository, never()).save(any());
        verify(enrollmentRepository).flush();
    }

    @Test
    @DisplayName("Gỡ HS: đổi sang DROPPED, không xóa; HS không đang học trong lớp → báo lỗi")
    void dropStudent() {
        Enrollment enrolled = CatalogTestData.enrollment(5L, section, hs260001, EnrollmentStatus.ENROLLED);
        when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L)).thenReturn(Optional.of(enrolled));

        service.drop(ADMIN, 10L, 1L, "127.0.0.1");

        assertThat(enrolled.getStatus()).isEqualTo(EnrollmentStatus.DROPPED);
        verify(enrollmentRepository, never()).delete(any());
        assertThatThrownBy(() -> service.drop(ADMIN, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.NOT_ENROLLED);
    }

    @Test
    @DisplayName("Ghi danh theo lớp hành chính: bỏ qua HS đã có và HS không đang học, ghi danh lại HS DROPPED")
    void enrollClassSkipsAndReEnrolls() {
        section.setMaxStudents((short) 10);
        Student enrolled = CatalogTestData.student(2L, "HS250101", StudentStatus.STUDYING);
        Student onLeave = CatalogTestData.student(3L, "HS250102", StudentStatus.ON_LEAVE);
        Student fresh = CatalogTestData.student(4L, "11A103", StudentStatus.STUDYING);
        Student droppedBefore = CatalogTestData.student(5L, "11A104", StudentStatus.STUDYING);
        Enrollment droppedRow = CatalogTestData.enrollment(9L, section, droppedBefore, EnrollmentStatus.DROPPED);
        classOf(List.of(enrolled, onLeave, fresh, droppedBefore));
        when(enrollmentRepository.findByClassSectionIdAndStudentIdIn(eq(10L), anyCollection())).thenReturn(List.of(
                CatalogTestData.enrollment(8L, section, enrolled, EnrollmentStatus.ENROLLED), droppedRow));
        enrolledCount(1);

        EnrollClassResult result = service.enrollClass(ADMIN, 10L, 14L, "127.0.0.1");

        assertThat(result).isEqualTo(new EnrollClassResult(1, 1, 1, 1));
        verify(enrollmentRepository, times(1)).save(any());
        assertThat(droppedRow.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Ghi danh theo lớp vượt sĩ số → từ chối cả lô, không ghi ai")
    void enrollClassOverCapacityRejectsWholeBatch() {
        section.setMaxStudents((short) 3);
        classOf(List.of(CatalogTestData.student(2L, "A1", StudentStatus.STUDYING),
                CatalogTestData.student(3L, "A2", StudentStatus.STUDYING),
                CatalogTestData.student(4L, "A3", StudentStatus.STUDYING)));
        enrolledCount(1);

        assertThatThrownBy(() -> service.enrollClass(ADMIN, 10L, 14L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không đủ chỗ: cần thêm 3 học sinh nhưng lớp chỉ còn 2 chỗ");
        verify(enrollmentRepository, never()).save(any());
        verify(enrollmentRepository, never()).flush();
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tổ trưởng chuyên môn và giáo viên không ghi danh, gỡ, ghi danh theo lớp được")
    void onlyAdminEnrolls() {
        assertThatThrownBy(() -> service.enroll(HEAD_SE, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.drop(HEAD_SE, 10L, 1L, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.enrollClass(HEAD_SE, 10L, 14L, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.candidates(HEAD_SE, 10L, "SE"))
                .isInstanceOf(AccessDeniedException.class);
        verify(classSectionRepository, never()).findByIdForUpdate(any());
    }

    private void enrolledCount(long count) {
        when(enrollmentRepository.countByClassSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(count);
    }

    private void classOf(List<Student> students) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(14L);
        schoolClass.setCode("11A1");
        when(schoolClassRepository.findById(14L)).thenReturn(Optional.of(schoolClass));
        when(studentRepository.findBySchoolClassIdOrderByStudentCodeAsc(14L)).thenReturn(students);
    }
}
