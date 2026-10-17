package vn.edu.fpt.contactbook.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Thay principal trong phiên hiện tại (ví dụ sau khi đổi mật khẩu) và lưu lại vào session.
 * Từ Spring Security 6, SecurityContext phải được lưu tường minh.
 */
@Component
public class SecurityContextRefresher {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    public void replacePrincipal(AppUserPrincipal principal, HttpServletRequest request,
                                 HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        repository.saveContext(context, request, response);
    }
}
