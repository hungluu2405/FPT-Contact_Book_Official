package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.edu.fpt.contactbook.dto.RoleAssignForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRoleServiceTest {

    private static final AppUserPrincipal ADMIN = UserServiceTest.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock StudentRepository studentRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ParentRepository parentRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogRepository auditLogRepository;

    private UserRoleService service;

    @BeforeEach
    void setUp() {
        UserService userService = mock(UserService.class);
        RoleProfiles roleProfiles = new RoleProfiles(studentRepository, teacherRepository, mock(MajorRepository.class),
                mock(SchoolClassRepository.class), departmentRepository);
        service = new UserRoleService(userService, userRoleRepository, roleRepository, studentRepository,
                teacherRepository, parentRepository, roleProfiles, new UserAccessPolicy(teacherRepository),
                new AuditService(auditLogRepository, userRepository));
        when(userService.findUser(any())).thenAnswer(call -> user(call.getArgument(0), true));
        Role admin = role(1L, RoleCodes.ADMIN);
        Role head = role(2L, RoleCodes.HEAD_OF_DEPARTMENT);
        Role parent = role(5L, RoleCodes.PARENT);
        when(roleRepository.findByCode(RoleCodes.ADMIN)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCode(RoleCodes.HEAD_OF_DEPARTMENT)).thenReturn(Optional.of(head));
        when(roleRepository.findByCode(RoleCodes.PARENT)).thenReturn(Optional.of(parent));
    }

    @Test
    @DisplayName("Không tự gỡ vai trò ADMIN của chính mình")
    void cannotRemoveOwnAdmin() {
        when(userRoleRepository.findById(10L)).thenReturn(Optional.of(userRole(ADMIN.getUserId(), RoleCodes.ADMIN, true)));
        when(userRoleRepository.countActiveAdmins()).thenReturn(3L);

        assertThatThrownBy(() -> service.remove(ADMIN, ADMIN.getUserId(), 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chính mình");
        verify(userRoleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Không gỡ ADMIN của quản trị viên đang hoạt động cuối cùng; gỡ được khi còn người khác")
    void lastActiveAdminKeepsRole() {
        UserRole other = userRole(7L, RoleCodes.ADMIN, true);
        when(userRoleRepository.findById(11L)).thenReturn(Optional.of(other));

        when(userRoleRepository.countActiveAdmins()).thenReturn(1L);
        assertThatThrownBy(() -> service.remove(ADMIN, 7L, 11L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cuối cùng");

        when(userRoleRepository.countActiveAdmins()).thenReturn(2L);
        service.remove(ADMIN, 7L, 11L, null);
        verify(userRoleRepository).delete(other);
    }

    @Test
    @DisplayName("Gỡ ADMIN của quản trị viên đã bị khóa không bị chặn bởi quy tắc 'cuối cùng'")
    void lockedAdminRoleCanBeRemoved() {
        UserRole locked = userRole(8L, RoleCodes.ADMIN, false);
        when(userRoleRepository.findById(12L)).thenReturn(Optional.of(locked));
        when(userRoleRepository.countActiveAdmins()).thenReturn(1L);

        service.remove(ADMIN, 8L, 12L, null);

        verify(userRoleRepository).delete(locked);
    }

    @Test
    @DisplayName("Vai trò của người khác (id không khớp) không gỡ được")
    void roleMustBelongToUser() {
        when(userRoleRepository.findById(13L)).thenReturn(Optional.of(userRole(9L, RoleCodes.PARENT, true)));

        assertThatThrownBy(() -> service.remove(ADMIN, 7L, 13L, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Gán trùng cặp (vai trò, bộ môn) bị từ chối")
    void duplicatePairIsRejected() {
        when(parentRepository.findByUserId(7L)).thenReturn(Optional.of(new Parent()));
        when(userRoleRepository.existsByUserIdAndRoleIdAndDepartmentIsNull(7L, 5L)).thenReturn(true);
        RoleAssignForm form = new RoleAssignForm();
        form.setRoleCode(RoleCodes.PARENT);

        assertThatThrownBy(() -> service.assign(ADMIN, 7L, form, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getField()).isEqualTo("roleCode");
    }

    @Test
    @DisplayName("Gán HEAD_OF_DEPARTMENT thiếu bộ môn bị từ chối")
    void headNeedsDepartment() {
        RoleAssignForm form = new RoleAssignForm();
        form.setRoleCode(RoleCodes.HEAD_OF_DEPARTMENT);

        assertThatThrownBy(() -> service.assign(ADMIN, 7L, form, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsKey("departmentId"));
    }

    private static User user(Long id, boolean active) {
        User user = new User();
        user.setId(id);
        user.setActive(active);
        return user;
    }

    private static Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }

    private static UserRole userRole(Long userId, String code, boolean active) {
        UserRole userRole = new UserRole();
        userRole.setUser(user(userId, active));
        userRole.setRole(role(1L, code));
        return userRole;
    }
}
