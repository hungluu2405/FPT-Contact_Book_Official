package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.fpt.contactbook.dto.CreatedUser;
import vn.edu.fpt.contactbook.dto.UserFilter;
import vn.edu.fpt.contactbook.dto.UserForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ của UserService với repository giả lập; RoleProfiles, UserAccessPolicy, AuditService,
 * TemporaryPasswordGenerator và BCrypt là code thật.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final AppUserPrincipal ADMIN = principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    private static final AppUserPrincipal HEAD = principal(2L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT,
            RoleCodes.TEACHER), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock StudentRepository studentRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock MajorRepository majorRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserService service;
    private Department se;

    @BeforeEach
    void setUp() {
        RoleProfiles roleProfiles = new RoleProfiles(studentRepository, teacherRepository, majorRepository,
                schoolClassRepository, departmentRepository);
        service = new UserService(userRepository, userRoleRepository, roleRepository, studentRepository,
                teacherRepository, parentRepository, parentStudentRepository, roleProfiles,
                new UserAccessPolicy(teacherRepository), new TemporaryPasswordGenerator(), passwordEncoder,
                new AuditService(auditLogRepository, userRepository));

        se = new Department();
        se.setId(1L);
        se.setCode("SE");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(se));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(departmentRepository.getReferenceById(1L)).thenReturn(se);
        for (String code : UserService.ROLE_ORDER) {
            Role role = new Role();
            role.setId((long) UserService.ROLE_ORDER.indexOf(code) + 1);
            role.setCode(code);
            when(roleRepository.findByCode(code)).thenReturn(Optional.of(role));
        }
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(100L);
            return user;
        });
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("Tạo người dùng TEACHER + HEAD_OF_DEPARTMENT: mật khẩu tạm, bắt đổi, vai trò kèm bộ môn")
    void createsTeacherAndHead() {
        UserForm form = teacherForm();

        CreatedUser created = service.create(ADMIN, form, "10.0.0.1");

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPhone()).isEqualTo("0911000111");
        assertThat(user.getValue().getEmail()).isNull();
        assertThat(user.getValue().isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(created.temporaryPassword(), user.getValue().getPasswordHash())).isTrue();

        ArgumentCaptor<Teacher> teacher = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(teacher.capture());
        assertThat(teacher.getValue().getTeacherCode()).isEqualTo("GV900");
        assertThat(teacher.getValue().getDepartment()).isSameAs(se);

        ArgumentCaptor<UserRole> roles = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository, times(2)).save(roles.capture());
        assertThat(roles.getAllValues()).extracting(ur -> ur.getRole().getCode())
                .containsExactly(RoleCodes.HEAD_OF_DEPARTMENT, RoleCodes.TEACHER);
        assertThat(roles.getAllValues()).allMatch(ur -> ur.getDepartment() == se);
    }

    @Test
    @DisplayName("Audit CREATE_USER không chứa mật khẩu tạm")
    void auditHasNoPassword() {
        CreatedUser created = service.create(ADMIN, teacherForm(), "10.0.0.1");

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditActions.CREATE_USER);
        assertThat(audit.getValue().getDetail()).isEqualTo("roles=HEAD_OF_DEPARTMENT,TEACHER")
                .doesNotContain(created.temporaryPassword());
        assertThat(created.toString()).doesNotContain(created.temporaryPassword());
    }

    @Test
    @DisplayName("Trùng số điện thoại, email, mã sinh viên: báo cùng lúc, theo từng ô")
    void duplicatesAreReportedPerField() {
        when(userRepository.existsByPhone("0911000111")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("a@fpt.edu.vn")).thenReturn(true);
        when(studentRepository.existsByStudentCode("SE999")).thenReturn(true);
        UserForm form = studentForm();
        form.setEmail("a@fpt.edu.vn");

        assertThatThrownBy(() -> service.create(ADMIN, form, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsOnlyKeys("phone", "email", "profile.studentCode"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Số điện thoại sai định dạng; +84 được chuẩn hóa")
    void phoneIsNormalizedAndValidated() {
        UserForm bad = teacherForm();
        bad.setPhone("12345");
        assertThatThrownBy(() -> service.create(ADMIN, bad, null))
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsKey("phone"));

        UserForm international = teacherForm();
        international.setPhone("+84 911 000 111");
        service.create(ADMIN, international, null);
        verify(userRepository).existsByPhone("0911000111");
    }

    @Test
    @DisplayName("Lớp không thuộc ngành của sinh viên bị từ chối")
    void classMustBelongToMajor() {
        Major se = major(1L);
        SchoolClass otherMajorClass = new SchoolClass();
        otherMajorClass.setId(14L);
        otherMajorClass.setMajor(major(15L));
        when(majorRepository.findById(1L)).thenReturn(Optional.of(se));
        when(schoolClassRepository.findById(14L)).thenReturn(Optional.of(otherMajorClass));
        UserForm form = studentForm();
        form.getProfile().setClassId(14L);

        assertThatThrownBy(() -> service.create(ADMIN, form, null))
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsEntry("profile.classId", "Lớp không thuộc ngành đã chọn"));
    }

    @Test
    @DisplayName("HEAD_OF_DEPARTMENT bắt buộc chọn bộ môn; phải chọn ít nhất một vai trò")
    void headNeedsDepartmentAndRolesRequired() {
        UserForm head = teacherForm();
        head.setHeadDepartmentId(null);
        assertThatThrownBy(() -> service.create(ADMIN, head, null))
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsKey("headDepartmentId"));

        UserForm none = teacherForm();
        none.setRoles(Set.of());
        assertThatThrownBy(() -> service.create(ADMIN, none, null))
                .extracting(e -> ((BusinessException) e).getFieldErrors())
                .satisfies(errors -> assertThat(errors).containsKey("roles"));
    }

    @Test
    @DisplayName("Trùng phát hiện ở database (thao tác đồng thời) đổi thành lỗi cạnh ô")
    void databaseUniqueViolationBecomesFieldError() {
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Violation of UNIQUE KEY constraint 'uq_users_phone'. Cannot insert duplicate key")));

        assertThatThrownBy(() -> service.create(ADMIN, teacherForm(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại đã được sử dụng")
                .extracting(e -> ((BusinessException) e).getField()).isEqualTo("phone");
    }

    @Test
    @DisplayName("Chỉ ADMIN được tạo, khóa, đặt lại mật khẩu")
    void onlyAdminCanWrite() {
        assertThatThrownBy(() -> service.create(HEAD, teacherForm(), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.lock(HEAD, 3L, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resetPassword(HEAD, 3L, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Không tự khóa chính mình")
    void cannotLockSelf() {
        assertThatThrownBy(() -> service.lock(ADMIN, ADMIN.getUserId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chính mình");
    }

    @Test
    @DisplayName("Không khóa quản trị viên đang hoạt động cuối cùng; khóa được khi còn quản trị viên khác")
    void lastActiveAdminCannotBeLocked() {
        User otherAdmin = activeUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(otherAdmin));
        when(userRoleRepository.existsByUserIdAndRoleCode(7L, RoleCodes.ADMIN)).thenReturn(true);

        when(userRoleRepository.countActiveAdmins()).thenReturn(1L);
        assertThatThrownBy(() -> service.lock(ADMIN, 7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cuối cùng");
        assertThat(otherAdmin.isActive()).isTrue();

        when(userRoleRepository.countActiveAdmins()).thenReturn(2L);
        service.lock(ADMIN, 7L, null);
        assertThat(otherAdmin.isActive()).isFalse();
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: mật khẩu tạm mới, bắt đổi, audit ghi người bị đặt lại")
    void resetPassword() {
        User target = activeUser(4L);
        target.setMustChangePassword(false);
        target.setPasswordHash("old");
        when(userRepository.findById(4L)).thenReturn(Optional.of(target));

        CreatedUser reset = service.resetPassword(ADMIN, 4L, null);

        assertThat(target.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(reset.temporaryPassword(), target.getPasswordHash())).isTrue();
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditActions.RESET_PASSWORD);
        assertThat(audit.getValue().getEntityId()).isEqualTo(4L);
        assertThat(audit.getValue().getDetail()).isNull();
    }

    @Test
    @DisplayName("HEAD xem chi tiết người ngoài bộ môn bị từ chối trước khi đọc dữ liệu")
    void headCannotViewOutsideScope() {
        Teacher outside = new Teacher();
        Department ia = new Department();
        ia.setId(38L);
        outside.setDepartment(ia);
        when(teacherRepository.findByUserId(50L)).thenReturn(Optional.of(outside));

        assertThatThrownBy(() -> service.detail(HEAD, 50L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(HEAD, 4L)).isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Danh sách của HEAD luôn bị giới hạn vào bộ môn của mình")
    void headListIsScoped() {
        when(userRepository.search(any(), any(), any(), any(), any(), any(), anyBoolean(),
                any(), any())).thenReturn(Page.empty());

        service.list(HEAD, new UserFilter(), 0);

        verify(userRepository).search(any(), any(), any(), any(), any(), any(),
                eq(true), eq(Set.of(1L)), any());
    }

    private static UserForm teacherForm() {
        UserForm form = new UserForm();
        form.setPhone("0911000111");
        form.setFullName("Giảng viên Mới");
        form.setRoles(new LinkedHashSet<>(List.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT)));
        form.setHeadDepartmentId(1L);
        form.getProfile().setTeacherCode(" gv900 ");
        form.getProfile().setTeacherDepartmentId(1L);
        return form;
    }

    private UserForm studentForm() {
        when(majorRepository.findById(1L)).thenReturn(Optional.of(major(1L)));
        UserForm form = new UserForm();
        form.setPhone("0911000111");
        form.setFullName("Sinh viên Mới");
        form.setRoles(Set.of(RoleCodes.STUDENT));
        form.getProfile().setStudentCode("se999");
        form.getProfile().setMajorId(1L);
        form.getProfile().setEnrollmentYear((short) 2026);
        return form;
    }

    private static Major major(Long id) {
        Major major = new Major();
        major.setId(id);
        return major;
    }

    private static User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        return user;
    }

    static AppUserPrincipal principal(Long id, Set<String> roles, Map<String, Set<Long>> departments) {
        return new AppUserPrincipal(id, "09000000" + id, "Người " + id, null, true, false, roles, departments);
    }
}
