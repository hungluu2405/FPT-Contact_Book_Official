package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SubjectAccessPolicyTest {

    private final SubjectAccessPolicy policy = new SubjectAccessPolicy(
            new UserAccessPolicy(mock(TeacherRepository.class)));

    @Test
    void adminManagesEveryDepartment() {
        AppUserPrincipal admin = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

        assertThatCode(() -> policy.requireCanManage(admin)).doesNotThrowAnyException();
        assertThat(policy.canManageDepartment(admin, 38L)).isTrue();
    }

    @Test
    void headManagesOnlyOwnDepartments() {
        AppUserPrincipal head = CatalogTestData.principal(2L, Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT),
                Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L), RoleCodes.TEACHER, Set.of(38L)));

        assertThatCode(() -> policy.requireCanManage(head)).doesNotThrowAnyException();
        assertThat(policy.canManageDepartment(head, 1L)).isTrue();
        // Bộ môn của vai trò TEACHER không mở rộng phạm vi trưởng bộ môn.
        assertThat(policy.canManageDepartment(head, 38L)).isFalse();
        assertThat(policy.canManageDepartment(head, null)).isFalse();
        assertThatThrownBy(() -> policy.requireDepartment(head, 38L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void headWithoutDepartmentAndOtherRolesAreForbidden() {
        AppUserPrincipal headWithoutDepartment = CatalogTestData.principal(3L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
                Map.of());
        AppUserPrincipal teacher = CatalogTestData.principal(4L, Set.of(RoleCodes.TEACHER),
                Map.of(RoleCodes.TEACHER, Set.of(1L)));
        AppUserPrincipal student = CatalogTestData.principal(5L, Set.of(RoleCodes.STUDENT), Map.of());

        for (AppUserPrincipal actor : new AppUserPrincipal[]{headWithoutDepartment, teacher, student}) {
            assertThatThrownBy(() -> policy.requireCanManage(actor)).isInstanceOf(AccessDeniedException.class);
            assertThat(policy.canManageDepartment(actor, 1L)).isFalse();
        }
    }
}
