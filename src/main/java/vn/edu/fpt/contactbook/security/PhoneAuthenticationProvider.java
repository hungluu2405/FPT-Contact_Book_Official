package vn.edu.fpt.contactbook.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import vn.edu.fpt.contactbook.service.AccountService;
import vn.edu.fpt.contactbook.service.AccountService.LoginFailure;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.nio.charset.StandardCharsets;

/**
 * Đăng nhập bằng số điện thoại.
 * <ol>
 *   <li>Số sai định dạng: lỗi chung, không truy vấn database, không đếm.</li>
 *   <li>Số đang khóa tạm: từ chối luôn, kể cả mật khẩu đúng.</li>
 *   <li>Kiểm tra mật khẩu TRƯỚC, rồi mới kiểm tra is_active, để thông báo "tài khoản đã bị khóa"
 *       chỉ hiện khi mật khẩu đúng.</li>
 * </ol>
 * Mọi lần thất bại ghi audit LOGIN_FAILED; lần sai làm số bị khóa tạm ghi thêm LOGIN_LOCKED (qua AccountService).
 * IP lấy từ WebAuthenticationDetails (remoteAddr, giả định 14).
 */
public class PhoneAuthenticationProvider extends DaoAuthenticationProvider {

    /** BCrypt chỉ xử lý tối đa 72 byte; dài hơn thì chắc chắn sai, không gọi encoder. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final LoginAttemptService loginAttemptService;
    private final AccountService accountService;

    public PhoneAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                                       LoginAttemptService loginAttemptService, AccountService accountService) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.loginAttemptService = loginAttemptService;
        this.accountService = accountService;
        setPreAuthenticationChecks(user -> {
        });
        setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("User is disabled");
            }
        });
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String ipAddress = authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : null;
        String phone = PhoneUtils.normalize(authentication.getName()).orElse(null);
        if (phone == null) {
            accountService.recordLoginFailure(null, LoginFailure.BAD_CREDENTIALS, ipAddress);
            throw new BadCredentialsException("Bad credentials");
        }
        if (loginAttemptService.isLocked(phone)) {
            accountService.recordLoginFailure(phone, LoginFailure.LOCKED, ipAddress);
            throw new LockedException("Temporarily locked");
        }

        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            badCredentials(phone, ipAddress);
            throw new BadCredentialsException("Bad credentials");
        }

        UsernamePasswordAuthenticationToken normalized =
                UsernamePasswordAuthenticationToken.unauthenticated(phone, password);
        normalized.setDetails(authentication.getDetails());
        try {
            Authentication result = super.authenticate(normalized);
            loginAttemptService.reset(phone);
            return result;
        } catch (BadCredentialsException e) {
            badCredentials(phone, ipAddress);
            throw e;
        } catch (DisabledException e) {
            accountService.recordLoginFailure(phone, LoginFailure.DISABLED, ipAddress);
            throw e;
        }
    }

    /** Đếm một lần sai và ghi audit; lần sai làm số bị khóa tạm ghi thêm LOGIN_LOCKED. */
    private void badCredentials(String phone, String ipAddress) {
        boolean locked = loginAttemptService.recordFailure(phone);
        accountService.recordLoginFailure(phone, LoginFailure.BAD_CREDENTIALS, ipAddress);
        if (locked) {
            accountService.recordLoginLocked(phone, LoginAttemptService.MAX_FAILURES,
                    LoginAttemptService.LOCK_DURATION.toMinutes(), ipAddress);
        }
    }
}
