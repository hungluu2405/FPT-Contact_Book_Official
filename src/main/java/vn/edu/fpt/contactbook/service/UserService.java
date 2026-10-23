package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CreatedUser;
import vn.edu.fpt.contactbook.dto.ParentLinkView;
import vn.edu.fpt.contactbook.dto.RoleAssignmentView;
import vn.edu.fpt.contactbook.dto.RoleProfileForm;
import vn.edu.fpt.contactbook.dto.UserDetailView;
import vn.edu.fpt.contactbook.dto.UserFilter;
import vn.edu.fpt.contactbook.dto.UserForm;
import vn.edu.fpt.contactbook.dto.UserListRow;
import vn.edu.fpt.contactbook.dto.UserRoleView;
import vn.edu.fpt.contactbook.dto.UserSummaryView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản lý người dùng (SRS FR-USER-01..07). Mọi thao tác nhận người đang thao tác (actor) để kiểm tra
 * phạm vi ở tầng service, không chỉ dựa vào URL.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    public static final int PAGE_SIZE = 20;
    public static final List<String> ROLE_ORDER = List.of(RoleCodes.ADMIN, RoleCodes.HEAD_OF_DEPARTMENT,
            RoleCodes.TEACHER, RoleCodes.STUDENT, RoleCodes.PARENT);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final RoleProfiles roleProfiles;
    private final UserAccessPolicy accessPolicy;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Danh sách có phân trang. HEAD_OF_DEPARTMENT luôn bị giới hạn vào giảng viên bộ môn mình. */
    @Transactional(readOnly = true)
    public Page<UserSummaryView> list(AppUserPrincipal actor, UserFilter filter, int page) {
        accessPolicy.requireCanList(actor);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        boolean scoped = !accessPolicy.isAdmin(actor);
        Set<Long> scope = scoped ? accessPolicy.headDepartmentIds(actor) : Set.of();
        if (scoped && scope.isEmpty()) {
            return Page.empty(pageable);
        }

        String keyword = filter.getKeyword();
        String roleCode = filter.getRole() != null && ROLE_ORDER.contains(filter.getRole()) ? filter.getRole() : null;
        Boolean active = switch (filter.getStatus() == null ? "" : filter.getStatus()) {
            case "ACTIVE" -> Boolean.TRUE;
            case "LOCKED" -> Boolean.FALSE;
            default -> null;
        };
        Page<UserListRow> rows = userRepository.search(
                LikePatterns.containsIgnoreCase(keyword),
                PhoneUtils.normalize(keyword).orElse(null),
                roleCode, filter.getDepartmentId(), filter.getClassId(), active,
                scoped, scoped ? scope : CatalogSupport.NO_IDS, pageable);

        List<Long> ids = rows.getContent().stream().map(UserListRow::id).toList();
        Map<Long, List<RoleAssignmentView>> rolesByUser = ids.isEmpty() ? Map.of()
                : userRoleRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.groupingBy(userRole -> userRole.getUser().getId(),
                        Collectors.mapping(UserService::toAssignment, Collectors.toList())));
        return rows.map(row -> new UserSummaryView(row, sortRoles(rolesByUser.getOrDefault(row.id(), List.of()))));
    }

    @Transactional(readOnly = true)
    public UserDetailView detail(AppUserPrincipal actor, Long userId) {
        accessPolicy.requireCanView(actor, userId);
        User user = findUser(userId);
        List<UserRoleView> roles = userRoleRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparingInt((UserRole ur) -> ROLE_ORDER.indexOf(ur.getRole().getCode()))
                        .thenComparing(ur -> ur.getDepartment() == null ? "" : ur.getDepartment().getCode()))
                .map(ur -> new UserRoleView(ur.getId(), ur.getRole().getCode(),
                        ur.getDepartment() == null ? null : ur.getDepartment().getCode(),
                        ur.getDepartment() == null ? null : ur.getDepartment().getName()))
                .toList();

        Student student = studentRepository.findByUserId(userId).orElse(null);
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        Parent parent = parentRepository.findByUserId(userId).orElse(null);

        List<ParentLinkView> parents = student == null ? List.of()
                : parentStudentRepository.findByIdStudentId(student.getId()).stream()
                .map(link -> new ParentLinkView(link.getParent().getId(), link.getParent().getUser().getId(),
                        link.getParent().getUser().getFullName(), link.getParent().getUser().getPhone(), null,
                        link.getRelationship(), link.isPrimary()))
                .toList();
        List<ParentLinkView> children = parent == null ? List.of()
                : parentStudentRepository.findByIdParentId(parent.getId()).stream()
                .map(link -> new ParentLinkView(link.getStudent().getId(), link.getStudent().getUser().getId(),
                        link.getStudent().getUser().getFullName(), link.getStudent().getUser().getPhone(),
                        link.getStudent().getStudentCode(), link.getRelationship(), link.isPrimary()))
                .toList();

        return new UserDetailView(user.getId(), user.getPhone(), user.getFullName(), user.getEmail(),
                user.getGender(), user.getDateOfBirth(), user.getAddress(), user.isActive(),
                user.isMustChangePassword(), user.getLastLoginAt(), user.getCreatedAt(), roles,
                student == null ? null : new UserDetailView.StudentProfile(student.getId(), student.getStudentCode(),
                        student.getMajor().getName(),
                        student.getSchoolClass() == null ? null : student.getSchoolClass().getCode(),
                        student.getEnrollmentYear(), student.getStatus()),
                teacher == null ? null : new UserDetailView.TeacherProfile(teacher.getId(), teacher.getTeacherCode(),
                        teacher.getDepartment().getName(), teacher.getAcademicTitle()),
                parent == null ? null : new UserDetailView.ParentProfile(parent.getId(), parent.getOccupation()),
                parents, children);
    }

    /**
     * Mọi kiểm tra nghiệp vụ trước khi tạo một người dùng mới (số điện thoại, email, vai trò, hồ sơ, trùng
     * với dữ liệu đã có). Chỉ đọc; trả về lỗi theo tên ô của UserForm, rỗng nếu hợp lệ.
     * Dùng chung cho tạo tay và nhập Excel để chỉ có một bộ kiểm tra.
     */
    @Transactional(readOnly = true)
    public Map<String, String> checkNewUser(UserForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkPhone(form.getPhone(), null, errors);
        checkEmail(form.getEmail(), null, errors);
        checkFullName(form.getFullName(), errors);

        Set<String> roleCodes = roleCodes(form);
        if (roleCodes.isEmpty()) {
            errors.putIfAbsent("roles", "Vui lòng chọn ít nhất một vai trò");
        } else if (!ROLE_ORDER.containsAll(roleCodes)) {
            errors.putIfAbsent("roles", "Vai trò không hợp lệ");
        }
        if (roleCodes.contains(RoleCodes.HEAD_OF_DEPARTMENT)
                && roleProfiles.department(form.getHeadDepartmentId()) == null) {
            errors.putIfAbsent("headDepartmentId", "Vui lòng chọn tổ chuyên môn cho vai trò tổ trưởng chuyên môn");
        }
        RoleProfileForm profile = form.getProfile();
        RoleProfiles.normalize(profile);
        if (roleCodes.stream().anyMatch(RoleProfiles::needsStudent)) {
            roleProfiles.checkStudent(profile, null, errors);
        }
        if (roleCodes.stream().anyMatch(RoleProfiles::needsTeacher)) {
            roleProfiles.checkTeacher(profile, null, errors);
        }
        return errors;
    }

    /**
     * Tạo người dùng kèm vai trò và hồ sơ; trả về mật khẩu tạm để tầng web hiện một lần.
     * Mật khẩu tạm không được ghi vào log hay audit.
     */
    @Transactional
    public CreatedUser create(AppUserPrincipal actor, UserForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        throwIfAny(checkNewUser(form));

        String phone = PhoneUtils.normalize(form.getPhone()).orElseThrow();
        String email = blankToNull(form.getEmail());
        Set<String> roleCodes = roleCodes(form);
        boolean needsStudent = roleCodes.stream().anyMatch(RoleProfiles::needsStudent);
        boolean needsTeacher = roleCodes.stream().anyMatch(RoleProfiles::needsTeacher);
        boolean needsParent = roleCodes.stream().anyMatch(RoleProfiles::needsParent);
        Department headDepartment = roleCodes.contains(RoleCodes.HEAD_OF_DEPARTMENT)
                ? roleProfiles.department(form.getHeadDepartmentId()) : null;
        RoleProfileForm profile = form.getProfile();

        String temporaryPassword = passwordGenerator.generate();
        User user = new User();
        applyPersonal(user, form, phone, email);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        try {
            userRepository.save(user);
            Teacher teacher = null;
            if (needsTeacher) {
                teacher = new Teacher();
                teacher.setUser(user);
                teacher = teacherRepository.save(roleProfiles.applyTeacher(teacher, profile));
            }
            if (needsStudent) {
                Student student = new Student();
                student.setUser(user);
                studentRepository.save(roleProfiles.applyStudent(student, profile));
            }
            if (needsParent) {
                Parent parent = new Parent();
                parent.setUser(user);
                parentRepository.save(roleProfiles.applyParent(parent, profile));
            }
            for (String code : ROLE_ORDER) {
                if (roleCodes.contains(code)) {
                    Department department = switch (code) {
                        case RoleCodes.HEAD_OF_DEPARTMENT -> headDepartment;
                        case RoleCodes.TEACHER -> teacher.getDepartment();
                        default -> null;
                    };
                    userRoleRepository.save(newUserRole(user, findRole(code), department));
                }
            }
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }

        auditService.log(actor.getUserId(), AuditActions.CREATE_USER, AuditActions.ENTITY_USER, user.getId(),
                "roles=" + ROLE_ORDER.stream().filter(roleCodes::contains).collect(Collectors.joining(",")),
                ipAddress);
        return new CreatedUser(user.getId(), temporaryPassword);
    }

    /** Dữ liệu điền sẵn cho form sửa. */
    @Transactional(readOnly = true)
    public UserForm editForm(AppUserPrincipal actor, Long userId) {
        accessPolicy.requireAdmin(actor);
        User user = findUser(userId);
        UserForm form = new UserForm();
        form.setPhone(user.getPhone());
        form.setFullName(user.getFullName());
        form.setGender(user.getGender());
        form.setDateOfBirth(user.getDateOfBirth());
        form.setEmail(user.getEmail());
        form.setAddress(user.getAddress());
        RoleProfileForm profile = form.getProfile();
        studentRepository.findByUserId(userId).ifPresent(student -> {
            profile.setStudentCode(student.getStudentCode());
            profile.setMajorId(student.getMajor().getId());
            profile.setClassId(student.getSchoolClass() == null ? null : student.getSchoolClass().getId());
            profile.setEnrollmentYear(student.getEnrollmentYear());
            profile.setStudentStatus(student.getStatus());
        });
        teacherRepository.findByUserId(userId).ifPresent(teacher -> {
            profile.setTeacherCode(teacher.getTeacherCode());
            profile.setTeacherDepartmentId(teacher.getDepartment().getId());
            profile.setAcademicTitle(teacher.getAcademicTitle());
        });
        parentRepository.findByUserId(userId).ifPresent(parent -> profile.setOccupation(parent.getOccupation()));
        return form;
    }

    /** Sửa thông tin cá nhân (kể cả số điện thoại) và các hồ sơ đang có; vai trò sửa ở màn hình riêng. */
    @Transactional
    public void update(AppUserPrincipal actor, Long userId, UserForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        User user = findUser(userId);
        Map<String, String> errors = new LinkedHashMap<>();
        String phone = checkPhone(form.getPhone(), userId, errors);
        String email = checkEmail(form.getEmail(), userId, errors);
        checkFullName(form.getFullName(), errors);

        Student student = studentRepository.findByUserId(userId).orElse(null);
        Teacher teacher = teacherRepository.findByUserId(userId).orElse(null);
        Parent parent = parentRepository.findByUserId(userId).orElse(null);
        RoleProfileForm profile = form.getProfile();
        RoleProfiles.normalize(profile);
        if (student != null) {
            roleProfiles.checkStudent(profile, student.getId(), errors);
        }
        if (teacher != null) {
            roleProfiles.checkTeacher(profile, teacher.getId(), errors);
        }
        throwIfAny(errors);

        applyPersonal(user, form, phone, email);
        if (student != null) {
            roleProfiles.applyStudent(student, profile);
        }
        if (teacher != null) {
            roleProfiles.applyTeacher(teacher, profile);
        }
        if (parent != null) {
            roleProfiles.applyParent(parent, profile);
        }
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
        auditService.log(actor.getUserId(), AuditActions.UPDATE_USER, AuditActions.ENTITY_USER, userId, null,
                ipAddress);
    }

    /**
     * Khóa tài khoản (is_active = 0). SERIALIZABLE để hai quản trị viên không thể cùng lúc khóa nhau
     * và làm hệ thống mất quản trị viên đang hoạt động cuối cùng.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void lock(AppUserPrincipal actor, Long userId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        if (userId.equals(actor.getUserId())) {
            throw new BusinessException("Không thể tự khóa tài khoản của chính mình");
        }
        User user = findUser(userId);
        if (!user.isActive()) {
            return;
        }
        if (userRoleRepository.existsByUserIdAndRoleCode(userId, RoleCodes.ADMIN)
                && userRoleRepository.countActiveAdmins() <= 1) {
            throw new BusinessException("Không thể khóa quản trị viên đang hoạt động cuối cùng");
        }
        user.setActive(false);
        auditService.log(actor.getUserId(), AuditActions.LOCK_USER, AuditActions.ENTITY_USER, userId, null,
                ipAddress);
    }

    @Transactional
    public void unlock(AppUserPrincipal actor, Long userId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        User user = findUser(userId);
        if (user.isActive()) {
            return;
        }
        user.setActive(true);
        auditService.log(actor.getUserId(), AuditActions.UNLOCK_USER, AuditActions.ENTITY_USER, userId, null,
                ipAddress);
    }

    /** Mật khẩu tạm mới, bắt đổi ở lần đăng nhập kế tiếp; trả về để hiện một lần. */
    @Transactional
    public CreatedUser resetPassword(AppUserPrincipal actor, Long userId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        User user = findUser(userId);
        String temporaryPassword = passwordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        auditService.log(actor.getUserId(), AuditActions.RESET_PASSWORD, AuditActions.ENTITY_USER, userId, null,
                ipAddress);
        return new CreatedUser(userId, temporaryPassword);
    }

    User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException(UserMessages.USER_NOT_FOUND));
    }

    Role findRole(String code) {
        return roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException("Missing role " + code));
    }

    static UserRole newUserRole(User user, Role role, Department department) {
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setDepartment(department);
        return userRole;
    }

    static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
    }

    private String checkPhone(String raw, Long existingUserId, Map<String, String> errors) {
        String phone = PhoneUtils.normalize(raw).orElse(null);
        if (phone == null) {
            errors.putIfAbsent("phone", UserMessages.PHONE_INVALID);
        } else if (existingUserId == null
                ? userRepository.existsByPhone(phone)
                : userRepository.existsByPhoneAndIdNot(phone, existingUserId)) {
            errors.putIfAbsent("phone", UserMessages.PHONE_TAKEN);
        }
        return phone;
    }

    private static void checkFullName(String fullName, Map<String, String> errors) {
        if (fullName == null || fullName.isBlank()) {
            errors.putIfAbsent("fullName", "Vui lòng nhập họ tên");
        }
    }

    private static Set<String> roleCodes(UserForm form) {
        return form.getRoles() == null ? Set.of() : new LinkedHashSet<>(form.getRoles());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String checkEmail(String raw, Long existingUserId, Map<String, String> errors) {
        String email = blankToNull(raw);
        if (email != null && (existingUserId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, existingUserId))) {
            errors.putIfAbsent("email", UserMessages.EMAIL_TAKEN);
        }
        return email;
    }

    private static void applyPersonal(User user, UserForm form, String phone, String email) {
        user.setPhone(phone);
        user.setFullName(form.getFullName().strip());
        user.setGender(form.getGender());
        user.setDateOfBirth(form.getDateOfBirth());
        user.setEmail(email);
        user.setAddress(form.getAddress() == null || form.getAddress().isBlank() ? null : form.getAddress().strip());
    }

    private static RoleAssignmentView toAssignment(UserRole userRole) {
        return new RoleAssignmentView(userRole.getRole().getCode(),
                userRole.getDepartment() == null ? null : userRole.getDepartment().getCode(),
                userRole.getDepartment() == null ? null : userRole.getDepartment().getName());
    }

    private static List<RoleAssignmentView> sortRoles(List<RoleAssignmentView> roles) {
        return roles.stream().sorted(Comparator.comparingInt(r -> ROLE_ORDER.indexOf(r.roleCode()))).toList();
    }
}
