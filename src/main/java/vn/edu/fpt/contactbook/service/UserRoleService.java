package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.RoleAssignForm;
import vn.edu.fpt.contactbook.dto.RoleProfileForm;
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
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gán và gỡ từng cặp (vai trò, bộ môn) cho người dùng (SRS FR-USER-05). Chỉ ADMIN.
 * Gỡ vai trò không xóa hồ sơ, để giữ lịch sử.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserService userService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentRepository parentRepository;
    private final RoleProfiles roleProfiles;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    /**
     * Bộ môn: bắt buộc với HEAD_OF_DEPARTMENT; tùy chọn với TEACHER (mặc định bộ môn trong hồ sơ giảng viên);
     * bỏ qua với vai trò khác. Người dùng chưa có hồ sơ cho vai trò thì hồ sơ trong form được tạo cùng lúc.
     */
    @Transactional
    public void assign(AppUserPrincipal actor, Long userId, RoleAssignForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        User user = userService.findUser(userId);
        Map<String, String> errors = new LinkedHashMap<>();

        Role role = form.getRoleCode() == null || !UserService.ROLE_ORDER.contains(form.getRoleCode()) ? null
                : roleRepository.findByCode(form.getRoleCode()).orElse(null);
        if (role == null) {
            errors.put("roleCode", "Vui lòng chọn vai trò");
            UserService.throwIfAny(errors);
        }
        String code = role.getCode();

        Department department = null;
        if (RoleCodes.HEAD_OF_DEPARTMENT.equals(code) || RoleCodes.TEACHER.equals(code)) {
            department = roleProfiles.department(form.getDepartmentId());
            if (department == null && RoleCodes.HEAD_OF_DEPARTMENT.equals(code)) {
                errors.putIfAbsent("departmentId", "Vui lòng chọn tổ chuyên môn cho vai trò tổ trưởng chuyên môn");
            }
        }

        RoleProfileForm profile = form.getProfile();
        RoleProfiles.normalize(profile);
        boolean createStudent = RoleProfiles.needsStudent(code) && studentRepository.findByUserId(userId).isEmpty();
        Teacher teacher = RoleProfiles.needsTeacher(code) ? teacherRepository.findByUserId(userId).orElse(null) : null;
        boolean createTeacher = RoleProfiles.needsTeacher(code) && teacher == null;
        boolean createParent = RoleProfiles.needsParent(code) && parentRepository.findByUserId(userId).isEmpty();
        if (createStudent) {
            roleProfiles.checkStudent(profile, null, errors);
        }
        if (createTeacher) {
            roleProfiles.checkTeacher(profile, null, errors);
        }
        UserService.throwIfAny(errors);

        try {
            if (createStudent) {
                Student student = new Student();
                student.setUser(user);
                studentRepository.save(roleProfiles.applyStudent(student, profile));
            }
            if (createTeacher) {
                teacher = new Teacher();
                teacher.setUser(user);
                teacher = teacherRepository.save(roleProfiles.applyTeacher(teacher, profile));
            }
            if (createParent) {
                Parent parent = new Parent();
                parent.setUser(user);
                parentRepository.save(roleProfiles.applyParent(parent, profile));
            }
            if (RoleCodes.TEACHER.equals(code) && department == null) {
                department = teacher.getDepartment();
            }
            boolean duplicate = department == null
                    ? userRoleRepository.existsByUserIdAndRoleIdAndDepartmentIsNull(userId, role.getId())
                    : userRoleRepository.existsByUserIdAndRoleIdAndDepartmentId(userId, role.getId(),
                    department.getId());
            if (duplicate) {
                throw new BusinessException(UserMessages.ROLE_ALREADY_ASSIGNED, "roleCode");
            }
            userRoleRepository.saveAndFlush(UserService.newUserRole(user, role, department));
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }

        auditService.log(actor.getUserId(), AuditActions.ASSIGN_ROLE, AuditActions.ENTITY_USER, userId,
                "role=" + code + (department == null ? "" : ",department=" + department.getCode()), ipAddress);
    }

    /**
     * Gỡ một cặp (vai trò, bộ môn). Không tự gỡ ADMIN của chính mình và không gỡ ADMIN của quản trị viên
     * đang hoạt động cuối cùng; SERIALIZABLE để hai quản trị viên không thể cùng lúc gỡ nhau.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void remove(AppUserPrincipal actor, Long userId, Long userRoleId, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        UserRole userRole = userRoleRepository.findById(userRoleId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy vai trò"));
        String code = userRole.getRole().getCode();
        if (RoleCodes.ADMIN.equals(code)) {
            if (userId.equals(actor.getUserId())) {
                throw new BusinessException("Không thể tự gỡ vai trò quản trị viên của chính mình");
            }
            if (userRole.getUser().isActive() && userRoleRepository.countActiveAdmins() <= 1) {
                throw new BusinessException("Không thể gỡ vai trò của quản trị viên đang hoạt động cuối cùng");
            }
        }
        userRoleRepository.delete(userRole);
        auditService.log(actor.getUserId(), AuditActions.REMOVE_ROLE, AuditActions.ENTITY_USER, userId,
                "role=" + code + (userRole.getDepartment() == null ? ""
                        : ",department=" + userRole.getDepartment().getCode()), ipAddress);
    }
}
