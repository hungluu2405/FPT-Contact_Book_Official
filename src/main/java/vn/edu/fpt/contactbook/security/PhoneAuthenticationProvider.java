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
 */
public class PhoneAuthenticationProvider extends DaoAuthenticationProvider {

    /** BCrypt chỉ xử lý tối đa 72 byte; dài hơn thì chắc chắn sai, không gọi encoder. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final LoginAttemptService loginAttemptService;

    public PhoneAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                                       LoginAttemptService loginAttemptService) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.loginAttemptService = loginAttemptService;
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
        String phone = PhoneUtils.normalize(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        if (loginAttemptService.isLocked(phone)) {
            throw new LockedException("Temporarily locked");
        }

        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            loginAttemptService.recordFailure(phone);
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
            loginAttemptService.recordFailure(phone);
            throw e;
        }
    }
}
