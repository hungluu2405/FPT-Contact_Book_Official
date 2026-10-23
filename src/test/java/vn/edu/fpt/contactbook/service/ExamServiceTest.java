package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.edu.fpt.contactbook.dto.ExamForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Exam;
import vn.edu.fpt.contactbook.model.enums.ExamType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.RoomRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lịch thi: xung đột thì không lưu, giờ kết thúc phải sau giờ bắt đầu, xóa hẳn và ghi audit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

    @Mock ExamRepository examRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock RoomRepository roomRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private ExamService service;
    private ClassSection section;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(new UserAccessPolicy(teacherRepository));
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        ScheduleConflictChecker checker = new ScheduleConflictChecker(classSessionRepository, examRepository,
                enrollmentRepository);
        ClassSessionService classSessionService = new ClassSessionService(classSessionRepository,
                classSectionRepository, roomRepository, attendanceRepository, classSectionService, checker,
                mock(ScheduleChangeNotifier.class), auditService);
        service = new ExamService(examRepository, classSessionService, classSectionService, checker, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se),
                CatalogTestData.semester(1L, "HK1-2627"), CatalogTestData.teacher(2L, "GV002", se), null,
                SectionStatus.OPEN);
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(enrollmentRepository.countSharedStudents(anyLong())).thenReturn(List.of());
        when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                .thenReturn(List.of());
        when(examRepository.findPossibleConflicts(any(), any(), any(), any(), anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Lịch thi trùng lịch thi khác của chính lớp → không lưu")
    void conflictSavesNothing() {
        Exam midterm = exam(5L, "07:30", "09:00");
        when(examRepository.findPossibleConflicts(any(), any(), any(), any(), anyCollection()))
                .thenReturn(List.of(midterm));

        assertThatThrownBy(() -> service.create(ADMIN, 10L, form("08:00", "10:00"), "127.0.0.1"))
                .isInstanceOfSatisfying(ScheduleConflictException.class,
                        e -> assertThat(e.getConflicts()).hasSize(1));
        verify(examRepository, never()).saveAndFlush(any());
    }

    @Test
    void endMustBeAfterStart() {
        assertThatThrownBy(() -> service.create(ADMIN, 10L, form("10:00", "09:00"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("endTime", ScheduleMessages.END_AFTER_START));
    }

    @Test
    @DisplayName("Xóa lịch thi: xóa hẳn dòng và ghi audit")
    void deleteRemovesAndAudits() {
        Exam exam = exam(5L, "07:30", "09:00");
        when(examRepository.findById(5L)).thenReturn(Optional.of(exam));

        service.delete(ADMIN, 10L, 5L, "127.0.0.1");

        verify(examRepository).delete(exam);
        verify(auditLogRepository).save(any());
    }

    private Exam exam(Long id, String start, String end) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setClassSection(section);
        exam.setExamType(ExamType.MIDTERM);
        exam.setExamDate(LocalDate.of(2026, 11, 2));
        exam.setStartTime(LocalTime.parse(start));
        exam.setEndTime(LocalTime.parse(end));
        return exam;
    }

    private static ExamForm form(String start, String end) {
        ExamForm form = new ExamForm();
        form.setExamType(ExamType.FINAL);
        form.setExamDate(LocalDate.of(2026, 11, 2));
        form.setStartTime(LocalTime.parse(start));
        form.setEndTime(LocalTime.parse(end));
        return form;
    }
}
