package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ClassSectionForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lớp học phần: đề xuất mã, giảng viên cùng bộ môn, sĩ số, khóa đổi môn/học kỳ, phạm vi trưởng bộ môn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassSectionServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private ClassSectionService service;
    private Subject toan10;
    private Subject iaSubject;
    private Semester hk1;
    private Semester sp27;
    private Teacher gv002;
    private Teacher iaTeacher;
    private ClassSection section;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(new UserAccessPolicy(teacherRepository));
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        service = new ClassSectionService(classSectionRepository, enrollmentRepository, subjectRepository,
                semesterRepository, teacherRepository, subjectService, policy, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        Department ia = CatalogTestData.department(38L, "IA");
        toan10 = CatalogTestData.subject(1L, "TOAN10", se);
        iaSubject = CatalogTestData.subject(50L, "IAO201", ia);
        hk1 = CatalogTestData.semester(1L, "HK1-2627");
        sp27 = CatalogTestData.semester(38L, "HK2-2627");
        gv002 = CatalogTestData.teacher(2L, "GV002", se);
        iaTeacher = CatalogTestData.teacher(20L, "GVIA01", ia);
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", toan10, hk1, gv002, 40, SectionStatus.OPEN);

        for (Subject subject : List.of(toan10, iaSubject)) {
            when(subjectRepository.findById(subject.getId())).thenReturn(Optional.of(subject));
            when(subjectRepository.existsById(subject.getId())).thenReturn(true);
        }
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(hk1));
        when(semesterRepository.findById(38L)).thenReturn(Optional.of(sp27));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(gv002));
        when(teacherRepository.findById(20L)).thenReturn(Optional.of(iaTeacher));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ClassSection saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
        when(classSectionRepository.findCodesLike(anyString())).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{1} → {2}")
    @CsvSource(delimiter = '|', value = {
            "TOAN10-HK1-2627-|''|TOAN10-HK1-2627-01",
            "TOAN10-HK1-2627-|TOAN10-HK1-2627-01,TOAN10-HK1-2627-02|TOAN10-HK1-2627-03",
            "TOAN10-HK1-2627-|TOAN10-HK1-2627-01,TOAN10-HK1-2627-03|TOAN10-HK1-2627-02",
            "TOAN10-HK1-2627-|toan10-hk1-2627-01,TOAN10-HK1-2627-01A,TOAN10-HK1-2627-|TOAN10-HK1-2627-02",
            "TOAN10-HK1-2627-|TOAN10-10A1-HK1|TOAN10-HK1-2627-01"
    })
    @DisplayName("Đề xuất mã: số thứ tự nhỏ nhất chưa dùng, bỏ qua mã không đúng mẫu")
    void nextCode(String prefix, String existing, String expected) {
        List<String> codes = existing.isEmpty() ? List.of() : List.of(existing.split(","));

        assertThat(ClassSectionService.nextCode(prefix, codes)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Để trống mã khi lưu: tự đề xuất theo môn và học kỳ")
    void createWithoutCodeUsesSuggestion() {
        when(classSectionRepository.findCodesLike("TOAN10-HK2-2627-%")).thenReturn(List.of("TOAN10-HK2-2627-01"));

        service.create(ADMIN, form(1L, 38L, 2L, null, 40), "127.0.0.1");

        ArgumentCaptor<ClassSection> saved = ArgumentCaptor.forClass(ClassSection.class);
        verify(classSectionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("TOAN10-HK2-2627-02");
        assertThat(saved.getValue().getStatus()).isEqualTo(SectionStatus.OPEN);
        verify(auditLogRepository).save(any());
    }

    @Test
    void suggestCodeNeedsSubjectAndSemester() {
        assertThat(service.suggestCode(ADMIN, 1L, 1L)).isEqualTo("TOAN10-HK1-2627-01");
        assertThatThrownBy(() -> service.suggestCode(ADMIN, 1L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getField()).isEqualTo("code"));
    }

    @Test
    @DisplayName("Giáo viên khác tổ chuyên môn của môn học → lỗi cạnh ô giáo viên, không lưu")
    void teacherFromOtherDepartmentRejected() {
        assertThatThrownBy(() -> service.create(ADMIN, form(1L, 1L, 20L, "TOAN10-HK1-2627-09", null), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("teacherId", SectionMessages.TEACHER_OTHER_DEPARTMENT));
        verify(classSectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateCodeRejected() {
        when(classSectionRepository.existsByCode("TOAN10-HK1-2627-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ADMIN, form(1L, 1L, 2L, " toan10-hk1-2627-01 ", null), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("code", SectionMessages.CODE_TAKEN));
    }

    @ParameterizedTest(name = "sĩ số {0}, đang học {1} → lỗi = {2}")
    @CsvSource(nullValues = "null", value = {
            "null, 30, false", "1, 0, false", "32767, 0, false", "0, 0, true", "-5, 0, true", "32768, 0, true",
            "30, 30, false", "29, 30, true"})
    void maxStudentsRules(Integer max, long enrolled, boolean error) {
        Map<String, String> errors = new LinkedHashMap<>();

        ClassSectionService.checkMaxStudents(max, enrolled, errors);

        assertThat(errors.containsKey("maxStudents")).isEqualTo(error);
    }

    @Test
    @DisplayName("Giảm sĩ số dưới số HS đang ENROLLED → từ chối; khóa dòng lớp trước khi đếm")
    void cannotReduceBelowEnrolled() {
        when(enrollmentRepository.countByClassSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(35L);

        assertThatThrownBy(() -> service.update(ADMIN, 10L, form(1L, 1L, 2L, "TOAN10-HK1-2627-01", 30), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors().get("maxStudents"))
                        .contains("35"));
        assertThat(section.getMaxStudents()).isEqualTo((short) 40);
        verify(classSectionRepository).findByIdForUpdate(10L);

        service.update(ADMIN, 10L, form(1L, 1L, 2L, "TOAN10-HK1-2627-01", 35), "127.0.0.1");
        assertThat(section.getMaxStudents()).isEqualTo((short) 35);
    }

    @Test
    @DisplayName("Lớp đã có ghi danh: không đổi được môn, học kỳ; vẫn đổi được giáo viên và trạng thái")
    void subjectAndSemesterLockedWhenEnrolled() {
        when(enrollmentRepository.existsByClassSectionId(10L)).thenReturn(true);
        Subject prn211 = CatalogTestData.subject(31L, "TIN11", toan10.getDepartment());
        when(subjectRepository.findById(31L)).thenReturn(Optional.of(prn211));
        when(subjectRepository.existsById(31L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ADMIN, 10L, form(31L, 38L, 2L, "TOAN10-HK1-2627-01", 40), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("subjectId", SectionMessages.SUBJECT_LOCKED)
                        .containsEntry("semesterId", SectionMessages.SEMESTER_LOCKED));
        assertThat(section.getSubject()).isSameAs(toan10);

        ClassSectionForm closeIt = form(1L, 1L, 2L, "TOAN10-HK1-2627-01", 40);
        closeIt.setStatus(SectionStatus.CLOSED);
        service.update(ADMIN, 10L, closeIt, "127.0.0.1");
        assertThat(section.getStatus()).isEqualTo(SectionStatus.CLOSED);
    }

    @Test
    void subjectChangeAllowedWithoutEnrollments() {
        when(enrollmentRepository.existsByClassSectionId(10L)).thenReturn(false);

        service.update(ADMIN, 10L, form(1L, 38L, 2L, "TOAN10-HK2-2627-01", null), "127.0.0.1");

        assertThat(section.getSemester()).isSameAs(sp27);
        assertThat(section.getMaxStudents()).isNull();
    }

    @Test
    @DisplayName("Tổ trưởng chuyên môn SE: lớp của môn tổ chuyên môn khác hoặc id không tồn tại → 403; ADMIN với id lạ → 404")
    void headScope() {
        ClassSection iaSection = CatalogTestData.section(11L, "IAO201-HK1-2627-01", iaSubject, hk1, iaTeacher, null,
                SectionStatus.OPEN);
        when(classSectionRepository.findById(11L)).thenReturn(Optional.of(iaSection));

        assertThat(service.detail(HEAD_SE, 10L).code()).isEqualTo("TOAN10-HK1-2627-01");
        assertThatThrownBy(() -> service.detail(HEAD_SE, 11L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.editForm(HEAD_SE, 11L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(HEAD_SE, 11L, form(50L, 1L, 20L, "X", null), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(HEAD_SE, 12345L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(HEAD_SE, form(50L, 1L, 20L, "IAO201-HK1-2627-02", null), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(ADMIN, 12345L)).isInstanceOf(NotFoundException.class);
        verify(classSectionRepository, never()).saveAndFlush(any());

        service.create(HEAD_SE, form(1L, 1L, 2L, null, 30), "127.0.0.1");
        verify(classSectionRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deadlock khi chạy SERIALIZABLE → thông báo thử lại")
    void lockFailureIsFriendlyError() {
        when(classSectionRepository.existsByCode(anyString())).thenThrow(new CannotAcquireLockException("deadlock"));

        assertThatThrownBy(() -> service.create(ADMIN, form(1L, 1L, 2L, "TOAN10-HK1-2627-05", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(SectionMessages.CONCURRENT_CHANGE);
        verify(auditLogRepository, never()).save(any());
    }

    private static ClassSectionForm form(Long subjectId, Long semesterId, Long teacherId, String code,
                                         Integer maxStudents) {
        ClassSectionForm form = new ClassSectionForm();
        form.setSubjectId(subjectId);
        form.setSemesterId(semesterId);
        form.setTeacherId(teacherId);
        form.setCode(code);
        form.setMaxStudents(maxStudents);
        return form;
    }
}
