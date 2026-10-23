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
import vn.edu.fpt.contactbook.dto.StudentGradeRow;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Điểm của sinh viên và phụ huynh: chỉ ô đã công bố (lọc ở truy vấn), trung bình trên điểm đã công bố, phạm vi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeViewServiceTest {

    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal PARENT = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());

    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock StudentRepository studentRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock ExamRepository examRepository;
    @Mock GradeComponentRepository gradeComponentRepository;
    @Mock GradeRepository gradeRepository;

    private GradeViewService service;
    private Enrollment enrollment;
    private List<GradeComponent> components;

    @BeforeEach
    void setUp() {
        ScheduleViewService scheduleViewService = new ScheduleViewService(semesterRepository, teacherRepository,
                studentRepository, parentRepository, parentStudentRepository, classSectionRepository,
                enrollmentRepository, classSessionRepository, examRepository);
        service = new GradeViewService(scheduleViewService, enrollmentRepository, gradeComponentRepository,
                gradeRepository);

        Department se = CatalogTestData.department(1L, "SE");
        Subject toan10 = CatalogTestData.subject(1L, "TOAN10", se);
        Semester hk1 = CatalogTestData.semester(1L, "HK1-2627");
        hk1.setCurrent(true);
        ClassSection section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", toan10, hk1,
                CatalogTestData.teacher(2L, "GV002", se), 40, SectionStatus.OPEN);
        enrollment = CatalogTestData.enrollment(100L, section,
                CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING), EnrollmentStatus.ENROLLED);
        components = List.of(GradeRuleTest.component(1L, "20", "10"), GradeRuleTest.component(2L, "30", "10"),
                GradeRuleTest.component(3L, "50", "10"));
        components.forEach(c -> c.setSubject(toan10));

        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(hk1));
        when(studentRepository.findByUserId(4L)).thenReturn(Optional.of(enrollment.getStudent()));
        Parent parent = new Parent();
        parent.setId(1L);
        when(parentRepository.findByUserId(5L)).thenReturn(Optional.of(parent));
        when(parentStudentRepository.existsByIdParentIdAndIdStudentId(1L, 1L)).thenReturn(true);
        when(enrollmentRepository.findAttendedInSemester(1L, 1L)).thenReturn(List.of(enrollment));
        when(gradeComponentRepository.findBySubjectIdInOrderByIdAsc(anyCollection())).thenReturn(components);
    }

    @Test
    @DisplayName("Chưa công bố gì: truy vấn đã lọc trả rỗng → mọi ô trống, trung bình Chưa đủ điểm")
    void nothingPublished() {
        when(gradeRepository.findPublishedByEnrollmentIds(anyCollection())).thenReturn(List.of());

        StudentGradeRow row = service.student(STUDENT, Audience.STUDENT, null, null).data().getFirst();

        assertThat(row.cells()).extracting(StudentGradeRow.Cell::score).containsOnlyNulls();
        assertThat(row.average()).isNull();
        verify(gradeRepository, never()).findBySectionId(any());
    }

    @Test
    @DisplayName("Chỉ hai trên ba đầu điểm đã công bố → hiện hai điểm, trung bình Chưa đủ điểm")
    void partiallyPublished() {
        when(gradeRepository.findPublishedByEnrollmentIds(anyCollection()))
                .thenReturn(List.of(published(0, "8"), published(1, "7")));

        StudentGradeRow row = service.student(STUDENT, Audience.STUDENT, null, null).data().getFirst();

        assertThat(row.cells()).extracting(StudentGradeRow.Cell::score)
                .containsExactly(new BigDecimal("8"), new BigDecimal("7"), null);
        assertThat(row.average()).isNull();
    }

    @Test
    @DisplayName("Đủ ba đầu điểm đã công bố → trung bình 8,2")
    void fullyPublished() {
        when(gradeRepository.findPublishedByEnrollmentIds(anyCollection()))
                .thenReturn(List.of(published(0, "8"), published(1, "7"), published(2, "9")));

        assertThat(service.student(PARENT, Audience.PARENT, 1L, null).data().getFirst().average())
                .isEqualByComparingTo("8.2");
    }

    @Test
    @DisplayName("Học sinh luôn xem của chính mình, studentId trong request bị bỏ qua")
    void studentIdIgnored() {
        when(gradeRepository.findPublishedByEnrollmentIds(anyCollection())).thenReturn(List.of());

        service.student(STUDENT, Audience.STUDENT, 2L, null);

        verify(enrollmentRepository).findAttendedInSemester(1L, 1L);
    }

    @Test
    @DisplayName("Phụ huynh xem con chưa liên kết → 403, không đọc điểm")
    void parentUnlinkedChild() {
        assertThatThrownBy(() -> service.student(PARENT, Audience.PARENT, 2L, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(gradeRepository, never()).findPublishedByEnrollmentIds(anyCollection());
    }

    private Grade published(int componentIndex, String score) {
        Grade grade = new Grade();
        grade.setEnrollment(enrollment);
        grade.setComponent(components.get(componentIndex));
        grade.setScore(new BigDecimal(score));
        grade.setPublished(true);
        return grade;
    }
}
