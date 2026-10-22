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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.JwtTokenService;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetCodes;
import vn.edu.fpt.contactbook.support.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ quên mật khẩu với mã, giới hạn và bộ đếm thật; chỉ giả lập database, email và audit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final String PHONE = "0900000004";
    private static final String IP = "127.0.0.1";
    private static final String NEW_PASSWORD = "MatKhauMoi2026";
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @Mock PasswordResetMailer mailer;

    private MutableClock clock;
    private PasswordResetCodes codes;
    private PasswordResetAttemptService attempts;
    private LoginAttemptService loginAttempts;
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-22T08:00:00Z"));
        codes = new PasswordResetCodes(JwtTokenService.signingKey("test-only-reset-key-not-a-secret-0123456789"),
                clock);
        attempts = new PasswordResetAttemptService(clock);
        loginAttempts = new LoginAttemptService(clock);
        service = new PasswordResetService(userRepository, ENCODER, auditService, codes, attempts, loginAttempts,
                mailer);

        user = new User();
        user.setId(4L);
        user.setPhone(PHONE);
        user.setEmail("student@fpt.edu.vn");
        user.setFullName("Lê Minh Sinh");
        user.setPasswordHash(ENCODER.encode("Fpt@12345"));
        user.setMustChangePassword(true);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Tài khoản có email: gửi mã đúng của tài khoản và ghi audit PASSWORD_RESET_REQUESTED")
    void sendsCodeToEmail() {
        service.requestCode("+84 900 000 004", IP);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq("student@fpt.edu.vn"), eq("Lê Minh Sinh"), code.capture(),
                eq(PasswordResetCodes.WINDOW));
        assertThat(codes.matches(4L, user.getPasswordHash(), code.getValue())).isTrue();
        verify(auditService).log(4L, AuditActions.PASSWORD_RESET_REQUESTED, AuditActions.ENTITY_USER, 4L, null, IP);
    }

    @Test
    @DisplayName("Số không tồn tại, sai định dạng, không có email hoặc đã khóa: không gửi gì và không báo lỗi")
    void silentForAccountsWithoutEmail() {
        when(userRepository.findByPhone("0999000111")).thenReturn(Optional.empty());
        service.requestCode("0999000111", IP);
        service.requestCode("12345", IP);

        user.setEmail("  ");
        clock.advance(Duration.ofMinutes(1));
        service.requestCode(PHONE, IP);

        user.setEmail("student@fpt.edu.vn");
        user.setActive(false);
        clock.advance(Duration.ofMinutes(1));
        service.requestCode(PHONE, IP);

        verifyNoInteractions(mailer, auditService);
    }

    @Test
    @DisplayName("Gửi mã quá dày bị chặn với mọi số, kể cả số không tồn tại")
    void limitsSendsForEveryPhone() {
        when(userRepository.findByPhone("0999000111")).thenReturn(Optional.empty());
        service.requestCode("0999000111", IP);

        assertThatThrownBy(() -> service.requestCode("0999000111", IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordResetService.SEND_LIMITED)
                .extracting(e -> ((BusinessException) e).getField()).isEqualTo("phone");
    }

    @Test
    @DisplayName("Gửi email lỗi: không báo lỗi cho người dùng, không ghi audit")
    void mailFailureStaysSilent() {
        doThrow(new IllegalStateException("smtp down")).when(mailer)
                .sendCode(anyString(), anyString(), anyString(), any());

        service.requestCode(PHONE, IP);

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mã đúng: đổi mật khẩu, bỏ cờ bắt buộc đổi, xóa bộ đếm sai, ghi audit PASSWORD_RESET_BY_EMAIL")
    void resetsPasswordWithValidCode() {
        String code = codes.issue(4L, user.getPasswordHash());
        loginAttempts.recordFailure(PHONE);
        attempts.recordFailure(PHONE);

        service.resetPassword(PHONE, " " + code + " ", NEW_PASSWORD, NEW_PASSWORD, IP);

        assertThat(ENCODER.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        verify(auditService).log(4L, AuditActions.PASSWORD_RESET_BY_EMAIL, AuditActions.ENTITY_USER, 4L, null, IP);
        // Bộ đếm đã xóa: cần đủ 5 lần sai mới khóa lại.
        for (int i = 1; i < 5; i++) {
            assertThat(attempts.recordFailure(PHONE)).isFalse();
            assertThat(loginAttempts.recordFailure(PHONE)).isFalse();
        }
    }

    @Test
    @DisplayName("Mã đã dùng không dùng lại được (mật khẩu đã đổi)")
    void codeIsSingleUse() {
        String code = codes.issue(4L, user.getPasswordHash());
        service.resetPassword(PHONE, code, NEW_PASSWORD, NEW_PASSWORD, IP);

        assertThatThrownBy(() -> service.resetPassword(PHONE, code, "MatKhauKhac2026", "MatKhauKhac2026", IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PasswordResetService.INVALID_CODE);
    }

    @Test
    @DisplayName("Mật khẩu mới sai quy tắc: lỗi theo ô, không tốn lượt nhập mã, không đổi gì")
    void validatesPasswordBeforeCode() {
        String hash = user.getPasswordHash();
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.resetPassword(PHONE, "000000", "short", "khac", IP))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getFieldErrors())
                            .containsKeys("newPassword", "confirmPassword"));
        }

        assertThat(attempts.isLocked(PHONE)).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo(hash);
    }

    @Test
    @DisplayName("Mã sai: báo lỗi ở ô code; lần sai thứ 5 khóa, khi khóa mã đúng cũng bị từ chối")
    void wrongCodesLockTheFlow() {
        String code = codes.issue(4L, user.getPasswordHash());
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> service.resetPassword(PHONE, wrong, NEW_PASSWORD, NEW_PASSWORD, IP))
                    .hasMessage(PasswordResetService.INVALID_CODE)
                    .extracting(e -> ((BusinessException) e).getField()).isEqualTo("code");
        }
        assertThatThrownBy(() -> service.resetPassword(PHONE, wrong, NEW_PASSWORD, NEW_PASSWORD, IP))
                .hasMessage(PasswordResetService.TOO_MANY_FAILURES);

        assertThatThrownBy(() -> service.resetPassword(PHONE, code, NEW_PASSWORD, NEW_PASSWORD, IP))
                .hasMessage(PasswordResetService.TOO_MANY_FAILURES);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Mã đúng nhưng hết hạn, hoặc tài khoản đã bị khóa: từ chối")
    void rejectsExpiredCodeAndInactiveAccount() {
        String code = codes.issue(4L, user.getPasswordHash());
        clock.advance(PasswordResetCodes.WINDOW.multipliedBy(2));
        assertThatThrownBy(() -> service.resetPassword(PHONE, code, NEW_PASSWORD, NEW_PASSWORD, IP))
                .hasMessage(PasswordResetService.INVALID_CODE);

        String fresh = codes.issue(4L, user.getPasswordHash());
        user.setActive(false);
        assertThatThrownBy(() -> service.resetPassword(PHONE, fresh, NEW_PASSWORD, NEW_PASSWORD, IP))
                .hasMessage(PasswordResetService.INVALID_CODE);
    }
}
