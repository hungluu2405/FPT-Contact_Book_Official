package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetCodes;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quên mật khẩu: gửi mã 6 chữ số tới email của tài khoản rồi đặt mật khẩu mới bằng mã đó (dùng chung cho web và
 * REST API). Yêu cầu mã luôn trả cùng một kết quả dù số điện thoại có tồn tại, có email hay không, để không lộ tài
 * khoản; tài khoản không có email vẫn phải nhờ ADMIN đặt lại mật khẩu (UC-03). Không ghi mã hay email ra log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final String SEND_LIMITED = "Bạn vừa yêu cầu mã, vui lòng đợi ít phút rồi thử lại";
    static final String INVALID_CODE = "Mã xác nhận không đúng hoặc đã hết hạn";
    static final String TOO_MANY_FAILURES = "Bạn đã nhập sai mã quá nhiều lần, vui lòng thử lại sau "
            + PasswordResetAttemptService.LOCK_DURATION.toMinutes() + " phút";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordResetCodes resetCodes;
    private final PasswordResetAttemptService attempts;
    private final LoginAttemptService loginAttempts;
    private final PasswordResetMailer mailer;

    /**
     * Gửi mã nếu số điện thoại thuộc một tài khoản đang hoạt động và có email; ngoài ra không làm gì. Chỉ báo lỗi khi
     * vượt giới hạn gửi mã (áp dụng cho mọi số, có tồn tại hay không). Gửi email lỗi chỉ ghi cảnh báo theo mã tài khoản.
     */
    @Transactional
    public void requestCode(String rawPhone, String ipAddress) {
        String phone = PhoneUtils.normalize(rawPhone).orElse(null);
        if (!attempts.tryAcquireSend(attemptKey(rawPhone, phone))) {
            throw new BusinessException(SEND_LIMITED, "phone");
        }
        Optional<User> account = phone == null ? Optional.empty() : userRepository.findByPhone(phone)
                .filter(User::isActive)
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank());
        if (account.isEmpty()) {
            return;
        }
        User user = account.get();
        String code = resetCodes.issue(user.getId(), user.getPasswordHash());
        try {
            mailer.sendCode(user.getEmail(), user.getFullName(), code, PasswordResetCodes.WINDOW);
        } catch (RuntimeException e) {
            log.warn("Password reset email could not be sent for user {}: {}", user.getId(), e.getClass().getName());
            return;
        }
        auditService.log(user.getId(), AuditActions.PASSWORD_RESET_REQUESTED, AuditActions.ENTITY_USER, user.getId(),
                null, ipAddress);
    }

    /**
     * Đặt mật khẩu mới bằng mã đã nhận. Kiểm tra quy tắc mật khẩu trước (sửa mật khẩu không tốn lượt nhập mã), rồi mới
     * kiểm tra mã. Thành công: bỏ cờ bắt buộc đổi mật khẩu, xóa bộ đếm đăng nhập sai; refresh token cũ hết hiệu lực vì
     * mật khẩu đã đổi.
     */
    @Transactional
    public void resetPassword(String rawPhone, String code, String newPassword, String confirmPassword,
                              String ipAddress) {
        String phone = PhoneUtils.normalize(rawPhone).orElse(null);
        String key = attemptKey(rawPhone, phone);
        if (attempts.isLocked(key)) {
            throw new BusinessException(TOO_MANY_FAILURES, "code");
        }
        List<PasswordPolicy.Violation> violations = PasswordPolicy.validate(null, newPassword, confirmPassword);
        if (!violations.isEmpty()) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            violations.forEach(v -> fieldErrors.putIfAbsent(v.field(), v.message()));
            throw new BusinessException(fieldErrors);
        }
        String givenCode = code == null ? null : code.trim();
        User user = phone == null ? null : userRepository.findByPhone(phone)
                .filter(User::isActive)
                .filter(candidate -> resetCodes.matches(candidate.getId(), candidate.getPasswordHash(), givenCode))
                .orElse(null);
        if (user == null) {
            boolean locked = attempts.recordFailure(key);
            throw new BusinessException(locked ? TOO_MANY_FAILURES : INVALID_CODE, "code");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        attempts.reset(key);
        loginAttempts.reset(phone);
        auditService.log(user.getId(), AuditActions.PASSWORD_RESET_BY_EMAIL, AuditActions.ENTITY_USER, user.getId(),
                null, ipAddress);
    }

    /** Số đã chuẩn hóa; số sai định dạng vẫn bị giới hạn theo chuỗi đã nhập. */
    private static String attemptKey(String rawPhone, String phone) {
        return phone != null ? phone : rawPhone == null ? "" : rawPhone.trim();
    }
}
