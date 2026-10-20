package vn.edu.fpt.contactbook.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Access token hợp lệ → AppUserPrincipal nạp lại từ database theo sub (users.id), để controller và service API
 * dùng chung principal, phạm vi dữ liệu với web. Tài khoản không còn hoặc đã bị khóa (is_active = 0): 401.
 * Tạo trong ApiSecurityConfig, không phải bean, để không lẫn vào chuỗi bảo mật web hay conversion service của MVC.
 */
@RequiredArgsConstructor
public class ApiPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserDetailsService userDetailsService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        AppUserPrincipal principal = userDetailsService.loadById(userId)
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        if (!principal.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
        principal.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Invalid subject");
        }
    }
}
