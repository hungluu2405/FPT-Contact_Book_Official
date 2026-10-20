package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    static final String SECRET = "unit-test-signing-key-0123456789abcdef";
    static final String HASH = "$2a$10$abcdefghijklmnopqrstuuJ0b8Vv3J9eQe9w5fZbq0m8Vn2a1Xy2e";
    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    final SecretKey key = JwtTokenService.signingKey(SECRET);
    final JwtTokenService service = service(key, NOW);

    @Test
    @DisplayName("Access token: sub = userId, token_type = access, sống đúng 15 phút")
    void issuesAccessToken() {
        JwtTokenService.TokenPair pair = service.issue(4L, HASH);

        Jwt access = service.accessDecoder().decode(pair.accessToken());
        assertThat(access.getSubject()).isEqualTo("4");
        assertThat(access.getClaimAsString(JwtTokenService.CLAIM_TOKEN_TYPE)).isEqualTo(JwtTokenService.ACCESS);
        assertThat(access.getClaimAsString(JwtTokenService.CLAIM_PASSWORD_FINGERPRINT)).isNull();
        assertThat(access.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(pair.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("Refresh token: token_type = refresh, có dấu vân tay mật khẩu, sống 14 ngày")
    void issuesRefreshToken() {
        Jwt refresh = service.decodeRefresh(service.issue(4L, HASH).refreshToken());

        assertThat(refresh.getSubject()).isEqualTo("4");
        assertThat(refresh.getClaimAsString(JwtTokenService.CLAIM_PASSWORD_FINGERPRINT))
                .isEqualTo(JwtTokenService.passwordFingerprint(HASH));
        assertThat(refresh.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    @DisplayName("Hai loại token không dùng thay cho nhau được")
    void tokenTypesAreNotInterchangeable() {
        JwtTokenService.TokenPair pair = service.issue(4L, HASH);

        assertThatThrownBy(() -> service.decodeRefresh(pair.accessToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.accessDecoder().decode(pair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token hết hạn bị từ chối")
    void rejectsExpiredToken() {
        JwtTokenService.TokenPair pair = service.issue(4L, HASH);
        JwtTokenService later = service(key, NOW.plus(Duration.ofMinutes(15)).plusSeconds(1));
        JwtTokenService muchLater = service(key, NOW.plus(Duration.ofDays(14)).plusSeconds(1));

        assertThatThrownBy(() -> later.accessDecoder().decode(pair.accessToken())).isInstanceOf(JwtException.class);
        assertThat(later.decodeRefresh(pair.refreshToken()).getSubject()).isEqualTo("4");
        assertThatThrownBy(() -> muchLater.decodeRefresh(pair.refreshToken())).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token ký bằng khóa khác, hoặc chuỗi rác, bị từ chối")
    void rejectsForeignOrMalformedToken() {
        JwtTokenService other = service(JwtTokenService.signingKey("another-signing-key-0123456789abcdef"), NOW);
        String foreign = other.issue(4L, HASH).accessToken();

        assertThatThrownBy(() -> service.accessDecoder().decode(foreign)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.accessDecoder().decode("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Dấu vân tay: 16 ký tự hex, đổi khi password_hash đổi")
    void fingerprintFollowsPasswordHash() {
        String fingerprint = JwtTokenService.passwordFingerprint(HASH);

        assertThat(fingerprint).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(JwtTokenService.passwordFingerprint(HASH)).isEqualTo(fingerprint);
        assertThat(JwtTokenService.passwordFingerprint(HASH + "x")).isNotEqualTo(fingerprint);
    }

    @Test
    @DisplayName("Khóa ký: để trống thì sinh ngẫu nhiên 32 byte; ngắn hơn 32 byte thì từ chối")
    void signingKeyRules() {
        SecretKey generated = JwtTokenService.signingKey("");
        SecretKey nullSecret = JwtTokenService.signingKey(null);

        assertThat(generated.getEncoded()).hasSize(32);
        assertThat(nullSecret.getEncoded()).hasSize(32).isNotEqualTo(generated.getEncoded());
        assertThat(JwtTokenService.signingKey("a".repeat(32)).getEncoded()).hasSize(32);
        assertThatThrownBy(() -> JwtTokenService.signingKey("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
    }

    private static JwtTokenService service(SecretKey key, Instant now) {
        return new JwtTokenService(key, Duration.ofMinutes(15), Duration.ofDays(14), Clock.fixed(now, ZoneOffset.UTC));
    }
}
