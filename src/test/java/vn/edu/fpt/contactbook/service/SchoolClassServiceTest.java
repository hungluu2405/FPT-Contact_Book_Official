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
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.SchoolClassForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lớp hành chính: chặn đổi ngành khi lớp đã có sinh viên, giảng viên chủ nhiệm theo mã, niên khóa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolClassServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD = CatalogTestData.principal(2L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
            Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock SchoolClassRepository schoolClassRepository;
    @Mock MajorRepository majorRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock StudentRepository studentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private SchoolClassService service;
    private Major se;
    private Major ia;
    private SchoolClass se1901;

    @BeforeEach
    void setUp() {
        service = new SchoolClassService(schoolClassRepository, majorRepository, teacherRepository, studentRepository,
                new UserAccessPolicy(teacherRepository), new AuditService(auditLogRepository, userRepository));
        se = major(1L, "SE");
        ia = major(14L, "IA");
        when(majorRepository.findById(1L)).thenReturn(Optional.of(se));
        when(majorRepository.findById(14L)).thenReturn(Optional.of(ia));
        se1901 = new SchoolClass();
        se1901.setId(1L);
        se1901.setMajor(se);
        se1901.setCode("10A1");
        se1901.setName("Lớp 10A1");
        se1901.setIntakeYear((short) 2019);
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(se1901));
        when(schoolClassRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            SchoolClass saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });

        Teacher gv001 = new Teacher();
        gv001.setId(2L);
        gv001.setTeacherCode("GV001");
        User user = new User();
        user.setFullName("Giáo viên 1");
        gv001.setUser(user);
        when(teacherRepository.findByTeacherCode("GV001")).thenReturn(Optional.of(gv001));
    }

    @Test
    @DisplayName("Lớp đã có học sinh: đổi khối bị chặn, lớp giữ nguyên")
    void majorLockedWhenClassHasStudents() {
        when(studentRepository.existsBySchoolClassId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ADMIN, 1L, form(14L, "10A1", 2019, null), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("majorId", CatalogMessages.CLASS_MAJOR_LOCKED));
        assertThat(se1901.getMajor()).isSameAs(se);
        verify(schoolClassRepository, never()).saveAndFlush(any());

        service.update(ADMIN, 1L, form(1L, "10A1", 2020, "gv001"), "127.0.0.1");
        assertThat(se1901.getIntakeYear()).isEqualTo((short) 2020);
        assertThat(se1901.getHomeroomTeacher().getTeacherCode()).isEqualTo("GV001");
    }

    @Test
    void majorChangeAllowedWhenClassIsEmpty() {
        when(studentRepository.existsBySchoolClassId(1L)).thenReturn(false);

        service.update(ADMIN, 1L, form(14L, "10A1", 2019, null), "127.0.0.1");

        assertThat(se1901.getMajor()).isSameAs(ia);
    }

    @Test
    @DisplayName("Tạo lớp: mã viết hoa, giáo viên chủ nhiệm tùy chọn; mã giáo viên không tồn tại → lỗi cạnh ô")
    void createWithOptionalHomeroom() {
        service.create(ADMIN, form(1L, " se2001 ", 2024, null), "127.0.0.1");
        ArgumentCaptor<SchoolClass> saved = ArgumentCaptor.forClass(SchoolClass.class);
        verify(schoolClassRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("SE2001");
        assertThat(saved.getValue().getHomeroomTeacher()).isNull();

        assertThatThrownBy(() -> service.create(ADMIN, form(1L, "SE2002", 2024, "GV999"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("homeroomTeacherCode", CatalogMessages.HOMEROOM_NOT_FOUND));
    }

    @ParameterizedTest(name = "năm vào trường {0} → hợp lệ = {1}")
    @CsvSource({"1999, false", "2000, true", "2100, true", "2101, false"})
    void intakeYearBoundaries(int year, boolean valid) {
        if (valid) {
            service.create(ADMIN, form(1L, "Y" + year, year, null), "127.0.0.1");
            verify(schoolClassRepository).saveAndFlush(any());
        } else {
            assertThatThrownBy(() -> service.create(ADMIN, form(1L, "Y" + year, year, null), "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getFieldErrors()).containsKey("intakeYear"));
        }
    }

    @Test
    void headIsForbidden() {
        assertThatThrownBy(() -> service.detail(HEAD, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(HEAD, form(1L, "X", 2024, null), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static SchoolClassForm form(Long majorId, String code, int intakeYear, String homeroomCode) {
        SchoolClassForm form = new SchoolClassForm();
        form.setMajorId(majorId);
        form.setCode(code);
        form.setName("Lớp " + code.strip());
        form.setIntakeYear(intakeYear);
        form.setHomeroomTeacherCode(homeroomCode);
        return form;
    }

    private static Major major(Long id, String code) {
        Major major = new Major();
        major.setId(id);
        major.setCode(code);
        major.setName("Khối " + code);
        return major;
    }
}
