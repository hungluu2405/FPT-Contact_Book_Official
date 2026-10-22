package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.contactbook.support.MutableClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetCodesTest {

    private static final String SECRET = "test-only-reset-key-not-a-secret-0123456789";
    private static final long USER_ID = 4L;
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuWm4wF0Pr1Z0e6qkZ8m0aD3e5f7g9h1i";

    private MutableClock clock;
    private PasswordResetCodes codes;

    @BeforeEach
    void setUp() {
        // Đầu một khung 10 phút, để tiến thời gian theo khung cho dễ đọc.
        clock = new MutableClock(Instant.parse("2026-10-22T08:00:00Z"));
        codes = new PasswordResetCodes(JwtTokenService.signingKey(SECRET), clock);
    }

    @Test
    @DisplayName("Mã có đúng 6 chữ số và khớp với chính người dùng, mật khẩu đó")
    void issuesSixDigitCode() {
        String code = codes.issue(USER_ID, HASH);

        assertThat(code).matches("\\d{6}");
        assertThat(codes.matches(USER_ID, HASH, code)).isTrue();
    }

    @Test
    @DisplayName("Mã còn hiệu lực ở khung kế tiếp, hết hiệu lực từ khung thứ hai sau đó")
    void expiresAfterTwoWindows() {
        String code = codes.issue(USER_ID, HASH);

        clock.advance(PasswordResetCodes.WINDOW.plusMinutes(9));
        assertThat(codes.matches(USER_ID, HASH, code)).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(codes.matches(USER_ID, HASH, code)).isFalse();
    }

    @Test
    @DisplayName("Đổi mật khẩu (password_hash khác) làm mã cũ mất hiệu lực")
    void invalidatedByPasswordChange() {
        String code = codes.issue(USER_ID, HASH);

        assertThat(codes.matches(USER_ID, HASH + "x", code)).isFalse();
    }

    @Test
    @DisplayName("Mã của người này không dùng được cho người khác")
    void boundToUser() {
        String code = codes.issue(USER_ID, HASH);

        assertThat(codes.matches(USER_ID + 1, HASH, code)).isFalse();
    }

    @Test
    @DisplayName("Khóa khác cho mã khác")
    void dependsOnKey() {
        PasswordResetCodes other = new PasswordResetCodes(
                JwtTokenService.signingKey(SECRET.replace('0', '1')), clock);

        assertThat(other.issue(USER_ID, HASH)).isNotEqualTo(codes.issue(USER_ID, HASH));
    }

    @Test
    @DisplayName("Mã rỗng, sai độ dài hoặc sai giá trị bị từ chối")
    void rejectsMalformedCodes() {
        String code = codes.issue(USER_ID, HASH);
        String wrong = String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);

        assertThat(codes.matches(USER_ID, HASH, null)).isFalse();
        assertThat(codes.matches(USER_ID, HASH, "")).isFalse();
        assertThat(codes.matches(USER_ID, HASH, code + "0")).isFalse();
        assertThat(codes.matches(USER_ID, HASH, code.substring(1))).isFalse();
        assertThat(codes.matches(USER_ID, HASH, wrong)).isFalse();
    }
}
