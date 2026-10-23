package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.SubjectForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ môn học: phạm vi trưởng bộ môn, chặn đổi bộ môn khi đã có lớp học phần, số tín chỉ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubjectServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    /** Trưởng bộ môn SE (id 1). */
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER),
            Map.of(RoleCodes.TEACHER, Set.of(1L)));

    @Mock SubjectRepository subjectRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private SubjectService service;
    private Department se;
    private Department ia;
    private Subject seSubject;
    private Subject iaSubject;

    @BeforeEach
    void setUp() {
        service = new SubjectService(subjectRepository, departmentRepository, classSectionRepository,
                new SubjectAccessPolicy(new UserAccessPolicy(teacherRepository)),
                new AuditService(auditLogRepository, userRepository));
        se = CatalogTestData.department(1L, "SE");
        ia = CatalogTestData.department(38L, "IA");
        seSubject = CatalogTestData.subject(1L, "TOAN10", se);
        iaSubject = CatalogTestData.subject(50L, "IAO201", ia);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(se));
        when(departmentRepository.findById(38L)).thenReturn(Optional.of(ia));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(seSubject));
        when(subjectRepository.findById(50L)).thenReturn(Optional.of(iaSubject));
        when(subjectRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Subject saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("Trưởng bộ môn xem/sửa môn bộ môn khác hoặc id không tồn tại → 403; ADMIN với id không tồn tại → 404")
    void headIsLimitedToOwnDepartment() {
        assertThat(service.detail(HEAD_SE, 1L).code()).isEqualTo("TOAN10");
        assertThatThrownBy(() -> service.detail(HEAD_SE, 50L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.editForm(HEAD_SE, 50L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(HEAD_SE, 50L, form(38L, "IAO201", 3), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(HEAD_SE, 12345L)).isInstanceOf(AccessDeniedException.class);

        assertThat(service.detail(ADMIN, 50L).code()).isEqualTo("IAO201");
        assertThatThrownBy(() -> service.detail(ADMIN, 12345L)).isInstanceOf(NotFoundException.class);
        assertThat(iaSubject.getName()).isEqualTo("Môn IAO201");
    }

    @Test
    void teacherWithoutHeadRoleIsForbidden() {
        assertThatThrownBy(() -> service.list(TEACHER, new CatalogFilter(), 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(TEACHER, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(TEACHER, form(1L, "NEW101", 3), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Trưởng bộ môn tạo môn ở bộ môn mình được; gửi bộ môn khác → 403, không lưu")
    void headCreatesOnlyInOwnDepartment() {
        service.create(HEAD_SE, form(1L, " swd392 ", 3), "127.0.0.1");
        verify(subjectRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(HEAD_SE, form(38L, "IAO202", 3), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(HEAD_SE, 1L, form(38L, "TOAN10", 3), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(seSubject.getDepartment()).isSameAs(se);
    }

    @Test
    void headListIsScopedToOwnDepartments() {
        when(subjectRepository.search(any(), any(), anyBoolean(), any(), any())).thenReturn(Page.empty());

        service.list(HEAD_SE, new CatalogFilter(), 0);
        verify(subjectRepository).search(any(), any(), eq(true), eq(Set.of(1L)), any());

        service.list(ADMIN, new CatalogFilter(), 0);
        verify(subjectRepository).search(any(), any(), eq(false), any(), any());
    }

    @Test
    @DisplayName("Môn đã có lớp học phần: không đổi được bộ môn, các trường khác vẫn sửa được")
    void departmentLockedWhenClassSectionsExist() {
        when(classSectionRepository.existsBySubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ADMIN, 1L, form(38L, "TOAN10", 3), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("departmentId", CatalogMessages.SUBJECT_DEPARTMENT_LOCKED));
        assertThat(seSubject.getDepartment()).isSameAs(se);
        verify(subjectRepository, never()).saveAndFlush(any());

        service.update(ADMIN, 1L, form(1L, "TOAN10", 4), "127.0.0.1");
        assertThat(seSubject.getCredits()).isEqualTo((short) 4);
    }

    @Test
    void departmentChangeAllowedWithoutClassSections() {
        when(classSectionRepository.existsBySubjectId(1L)).thenReturn(false);

        service.update(ADMIN, 1L, form(38L, "TOAN10", 3), "127.0.0.1");

        assertThat(seSubject.getDepartment()).isSameAs(ia);
    }

    @ParameterizedTest(name = "{0} tín chỉ → hợp lệ = {1}")
    @CsvSource({"0, false", "1, true", "20, true", "21, false", "-3, false"})
    void creditsBoundaries(int credits, boolean valid) {
        if (valid) {
            service.create(ADMIN, form(1L, "CR" + (credits + 10), credits), "127.0.0.1");
            verify(subjectRepository).saveAndFlush(any());
        } else {
            assertThatThrownBy(() -> service.create(ADMIN, form(1L, "CRX", credits), "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getFieldErrors()).containsEntry("credits", CatalogMessages.CREDITS_RANGE));
        }
    }

    @Test
    void duplicateCodeIsFieldError() {
        when(subjectRepository.existsByCodeAndIdNot(eq("TIN11"), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> service.update(ADMIN, 1L, form(1L, "tin11", 3), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("code", CatalogMessages.SUBJECT_CODE_TAKEN));
    }

    private static SubjectForm form(Long departmentId, String code, int credits) {
        SubjectForm form = new SubjectForm();
        form.setDepartmentId(departmentId);
        form.setCode(code);
        form.setName("Môn " + code.strip());
        form.setCredits(credits);
        return form;
    }
}
