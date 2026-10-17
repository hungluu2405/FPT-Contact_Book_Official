package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.RoleAssignmentView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.Comparator;
import java.util.List;

/**
 * Tài khoản của chính người đang đăng nhập: ghi nhận đăng nhập, đổi mật khẩu, xem vai trò.
 * Mọi thao tác chỉ nhận userId của chính người dùng (lấy từ principal ở tầng web).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public void recordLogin(Long userId, String ipAddress) {
        User user = findUser(userId);
        user.setLastLoginAt(AppTime.now());
        auditService.log(userId, AuditActions.LOGIN, AuditActions.ENTITY_USER, userId, null, ipAddress);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword,
                               String ipAddress) {
        List<PasswordPolicy.Violation> violations =
                PasswordPolicy.validate(currentPassword, newPassword, confirmPassword);
        if (!violations.isEmpty()) {
            PasswordPolicy.Violation first = violations.getFirst();
            throw new BusinessException(first.message(), first.field());
        }
        User user = findUser(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu hiện tại không đúng", "currentPassword");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        auditService.log(userId, AuditActions.CHANGE_PASSWORD, AuditActions.ENTITY_USER, userId, null, ipAddress);
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentView> roleAssignments(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(userRole -> new RoleAssignmentView(
                        userRole.getRole().getCode(),
                        userRole.getDepartment() == null ? null : userRole.getDepartment().getCode(),
                        userRole.getDepartment() == null ? null : userRole.getDepartment().getName()))
                .sorted(Comparator.comparing(RoleAssignmentView::roleCode))
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
    }
}
