package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Xem lịch: phạm vi phụ huynh (chỉ con đã liên kết), sinh viên chỉ thấy lớp mình đang học, tuần từ thứ Hai.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleViewServiceTest {

    static final AppUserPrincipal PARENT = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());

    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock StudentRepository studentRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock ExamRepository examRepository;

    private ScheduleViewService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleViewService(semesterRepository, teacherRepository, studentRepository, parentRepository,
                parentStudentRepository, classSectionRepository, enrollmentRepository, classSessionRepository,
                examRepository);
        Semester hk1 = CatalogTestData.semester(1L, "HK1-2627");
        hk1.setStartDate(LocalDate.of(2026, 9, 7));
        hk1.setEndDate(LocalDate.of(2026, 12, 20));
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(hk1));
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(hk1));
        Parent parent = new Parent();
        parent.setId(1L);
        when(parentRepository.findByUserId(5L)).thenReturn(Optional.of(parent));
        when(parentStudentRepository.existsByIdParentIdAndIdStudentId(1L, 1L)).thenReturn(true);
        when(classSessionRepository.findForSections(anyCollection(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Phụ huynh xem con chưa liên kết → 403, không truy vấn lịch")
    void parentCannotSeeUnlinkedStudent() {
        assertThatThrownBy(() -> service.week(PARENT, Audience.PARENT, 2L, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.exams(PARENT, Audience.PARENT, 2L, null, 0))
                .isInstanceOf(AccessDeniedException.class);
        verify(classSessionRepository, never()).findForSections(anyCollection(), any(), any());
    }

    @Test
    @DisplayName("Người không phải phụ huynh dùng đường dẫn của phụ huynh → 403")
    void nonParentIsForbidden() {
        assertThatThrownBy(() -> service.week(STUDENT, Audience.PARENT, 1L, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Phụ huynh xem con đã liên kết: lấy lớp con đang học trong học kỳ")
    void parentSeesLinkedChild() {
        when(enrollmentRepository.findEnrolledSectionIds(1L, 1L)).thenReturn(List.of(1L));

        ScheduleWeek week = service.week(PARENT, Audience.PARENT, 1L, null, LocalDate.of(2026, 9, 10)).data();

        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(week.weekStart().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(week.days()).hasSize(7);
        assertThat(week.previous()).isEqualTo(LocalDate.of(2026, 8, 31));
        verify(classSessionRepository).findForSections(eq(List.of(1L)), eq(LocalDate.of(2026, 9, 7)),
                eq(LocalDate.of(2026, 9, 13)));
    }

    @Test
    @DisplayName("Học sinh chỉ thấy lớp mình đang học; chưa học lớp nào thì truy vấn với id giả, không lỗi")
    void studentWithoutSections() {
        Student me = CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING);
        when(studentRepository.findByUserId(4L)).thenReturn(Optional.of(me));
        when(enrollmentRepository.findEnrolledSectionIds(1L, 1L)).thenReturn(List.of());

        service.week(STUDENT, Audience.STUDENT, null, null, LocalDate.of(2026, 9, 10));

        verify(classSessionRepository).findForSections(eq(List.of(-1L)), any(), any());
    }

    @Test
    @DisplayName("Lịch thi sắp tới: chỉ từ hôm nay, tối đa limit kỳ thi, trong lớp con đang học")
    void upcomingExamsFromToday() {
        when(enrollmentRepository.findEnrolledSectionIds(1L, 1L)).thenReturn(List.of(1L, 2L));
        when(examRepository.findUpcomingForSections(anyCollection(), any(), any())).thenReturn(List.of());

        assertThat(service.upcomingExams(PARENT, Audience.PARENT, 1L, 3)).isEmpty();

        verify(examRepository).findUpcomingForSections(eq(List.of(1L, 2L)), eq(AppTime.today()),
                eq(PageRequest.of(0, 3)));
    }

    @Test
    @DisplayName("Lịch thi sắp tới của con chưa liên kết → 403")
    void upcomingExamsOfUnlinkedChild() {
        assertThatThrownBy(() -> service.upcomingExams(PARENT, Audience.PARENT, 2L, 3))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Học kỳ hiện tại: có thì trả về, chưa có học kỳ nào thì rỗng")
    void currentSemester() {
        assertThat(service.currentSemester()).hasValueSatisfying(s -> assertThat(s.code()).isEqualTo("HK1-2627"));

        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(semesterRepository.findAllByOrderByStartDateDesc()).thenReturn(List.of());

        assertThat(service.currentSemester()).isEmpty();
    }
}
