package vn.edu.fpt.contactbook.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Phát hành và kiểm tra JWT cho REST API, ký HS256.
 * <ul>
 *   <li>Access token: sub = users.id, token_type = access. Mỗi request nạp lại người dùng từ database
 *       (ApiPrincipalConverter), nên khóa tài khoản hay đổi vai trò có hiệu lực ngay.</li>
 *   <li>Refresh token: token_type = refresh, kèm pwf (dấu vân tay của password_hash). Đổi hoặc reset mật khẩu
 *       làm mọi refresh token cũ hết hiệu lực. Không lưu token ở database (giả định trong docs/ASSUMPTIONS.md).</li>
 * </ul>
 * Hai loại token không dùng thay cho nhau được: mỗi decoder chỉ nhận đúng một token_type.
 */
@Slf4j
public class JwtTokenService {

    static final String ISSUER = "fpt-contact-book";
    static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_PASSWORD_FINGERPRINT = "pwf";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";
    /** HS256 cần khóa tối thiểu 256 bit. */
    static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder refreshDecoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public JwtTokenService(SecretKey key, Duration accessTokenTtl, Duration refreshTokenTtl, Clock clock) {
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.accessDecoder = decoder(key, ACCESS, clock);
        this.refreshDecoder = decoder(key, REFRESH, clock);
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    /**
     * Khóa ký từ app.jwt.secret. Để trống: sinh khóa ngẫu nhiên (token mất hiệu lực khi khởi động lại).
     * Ngắn hơn 32 byte: từ chối khởi động.
     */
    public static SecretKey signingKey(String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set; using a random signing key. Tokens become invalid after restart.");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** 16 ký tự hex đầu của SHA-256(password_hash): đổi mật khẩu là đổi dấu vân tay, không lộ chính mã băm. */
    public static String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public TokenPair issue(Long userId, String passwordHash) {
        Instant now = clock.instant();
        String access = encode(JwtClaimsSet.builder()
                .issuer(ISSUER).subject(userId.toString()).issuedAt(now).expiresAt(now.plus(accessTokenTtl))
                .claim(CLAIM_TOKEN_TYPE, ACCESS)
                .build());
        String refresh = encode(JwtClaimsSet.builder()
                .issuer(ISSUER).subject(userId.toString()).issuedAt(now).expiresAt(now.plus(refreshTokenTtl))
                .claim(CLAIM_TOKEN_TYPE, REFRESH)
                .claim(CLAIM_PASSWORD_FINGERPRINT, passwordFingerprint(passwordHash))
                .build());
        return new TokenPair(access, refresh, accessTokenTtl.toSeconds());
    }

    /** Decoder dùng cho chuỗi bảo mật /api/**: chỉ nhận access token. */
    public JwtDecoder accessDecoder() {
        return accessDecoder;
    }

    /** Giải mã refresh token; sai chữ ký, hết hạn hay sai loại token ném JwtException. */
    public Jwt decodeRefresh(String token) throws JwtException {
        return refreshDecoder.decode(token);
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtDecoder decoder(SecretKey key, String tokenType, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<String>(CLAIM_TOKEN_TYPE, tokenType::equals));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
