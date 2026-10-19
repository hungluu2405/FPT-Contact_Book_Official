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
 * Đăng nhập thất bại và khóa tạm (FR-SYS-01) cũng được ghi ở đây, theo số điện thoại đã chuẩn hóa.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Lý do đăng nhập thất bại do tầng xác thực báo; UNKNOWN_ACCOUNT, INVALID_PHONE do service tự xác định. */
    public enum LoginFailure { BAD_CREDENTIALS, DISABLED, LOCKED }

    @Transactional
    public void recordLogin(Long userId, String ipAddress) {
        User user = findUser(userId);
        user.setLastLoginAt(AppTime.now());
        auditService.log(userId, AuditActions.LOGIN, AuditActions.ENTITY_USER, userId, null, ipAddress);
    }

    /**
     * Audit LOGIN_FAILED. phone là số đã chuẩn hóa, null nếu số nhập sai định dạng (reason=INVALID_PHONE). Số không
     * khớp tài khoản nào thì không gắn tài khoản; BAD_CREDENTIALS khi đó ghi thành UNKNOWN_ACCOUNT. Không bao giờ ghi
     * mật khẩu, và không ghi lại số điện thoại đã nhập (giả định 113).
     */
    @Transactional
    public void recordLoginFailure(String phone, LoginFailure failure, String ipAddress) {
        Long userId = phone == null ? null : userRepository.findByPhone(phone).map(User::getId).orElse(null);
        String reason = phone == null ? "INVALID_PHONE"
                : userId == null && failure == LoginFailure.BAD_CREDENTIALS ? "UNKNOWN_ACCOUNT" : failure.name();
        auditService.log(userId, AuditActions.LOGIN_FAILED, userId == null ? null : AuditActions.ENTITY_USER, userId,
                "reason=" + reason, ipAddress);
    }

    /** Audit LOGIN_LOCKED khi lần sai vừa rồi làm số điện thoại bị khóa tạm; gắn tài khoản nếu số tồn tại. */
    @Transactional
    public void recordLoginLocked(String phone, int failures, long lockMinutes, String ipAddress) {
        Long userId = userRepository.findByPhone(phone).map(User::getId).orElse(null);
        auditService.log(userId, AuditActions.LOGIN_LOCKED, userId == null ? null : AuditActions.ENTITY_USER, userId,
                "failures=" + failures + ", lockMinutes=" + lockMinutes, ipAddress);
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
