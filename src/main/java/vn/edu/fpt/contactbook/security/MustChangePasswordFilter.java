package vn.edu.fpt.contactbook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Người dùng có must_change_password = 1 bị chuyển về /change-password ở mọi trang,
 * trừ trang đổi mật khẩu, đăng xuất, trang lỗi và tài nguyên tĩnh.
 * Không đánh dấu @Component để không bị đăng ký thêm một lần ngoài chuỗi Spring Security.
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    static final String CHANGE_PASSWORD_PATH = "/change-password";

    private static final Set<String> EXEMPT_PATHS =
            Set.of(CHANGE_PASSWORD_PATH, "/logout", "/login", "/error", "/favicon.ico");
    private static final List<String> EXEMPT_PREFIXES = List.of("/css/", "/js/", "/webjars/", "/images/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && !isExempt(request.getRequestURI().substring(request.getContextPath().length()))) {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_PATH);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isExempt(String path) {
        return EXEMPT_PATHS.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
