package vn.edu.fpt.contactbook.security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

/**
 * Mã xác nhận 6 chữ số để đặt lại mật khẩu qua email, không lưu ở database. Mã là HMAC-SHA256 của (mã người dùng,
 * dấu vân tay mật khẩu hiện tại, khung thời gian 10 phút), rút gọn như HOTP (RFC 4226). Mã của khung hiện tại và khung
 * liền trước đều được chấp nhận nên mã còn hiệu lực 10–20 phút; đổi mật khẩu là đổi dấu vân tay nên mã đã dùng hoặc mã
 * cũ tự mất hiệu lực.
 */
public class PasswordResetCodes {

    public static final Duration WINDOW = Duration.ofMinutes(10);
    static final int DIGITS = 6;
    private static final int MODULO = 1_000_000;
    /** Tách miền với chữ ký JWT khi dùng chung khóa app.jwt.secret. */
    private static final String PURPOSE = "password-reset";

    private final SecretKey key;
    private final Clock clock;

    public PasswordResetCodes(SecretKey key, Clock clock) {
        this.key = key;
        this.clock = clock;
    }

    public String issue(Long userId, String passwordHash) {
        return codeFor(userId, passwordHash, currentWindow());
    }

    public boolean matches(Long userId, String passwordHash, String code) {
        if (code == null || code.length() != DIGITS) {
            return false;
        }
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        long window = currentWindow();
        boolean current = MessageDigest.isEqual(given,
                codeFor(userId, passwordHash, window).getBytes(StandardCharsets.UTF_8));
        boolean previous = MessageDigest.isEqual(given,
                codeFor(userId, passwordHash, window - 1).getBytes(StandardCharsets.UTF_8));
        return current | previous;
    }

    private long currentWindow() {
        return clock.instant().getEpochSecond() / WINDOW.toSeconds();
    }

    private String codeFor(Long userId, String passwordHash, long window) {
        String message = PURPOSE + "|" + userId + "|" + JwtTokenService.passwordFingerprint(passwordHash) + "|"
                + window;
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%0" + DIGITS + "d", binary % MODULO);
    }
}
