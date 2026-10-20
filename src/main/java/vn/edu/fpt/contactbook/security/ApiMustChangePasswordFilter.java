package vn.edu.fpt.contactbook.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.fpt.contactbook.exception.ApiErrorCode;

import java.io.IOException;

/**
 * Bản API của MustChangePasswordFilter: người dùng có must_change_password = 1 chỉ gọi được nhóm /api/v1/auth/**,
 * GET /api/v1/me và POST /api/v1/me/password; mọi API khác trả 403 PASSWORD_CHANGE_REQUIRED.
 * Không đánh dấu @Component để không bị đăng ký thêm một lần ngoài chuỗi Spring Security.
 */
public class ApiMustChangePasswordFilter extends OncePerRequestFilter {

    static final String AUTH_PREFIX = "/api/v1/auth/";
    static final String ME_PATH = "/api/v1/me";
    static final String CHANGE_PASSWORD_PATH = "/api/v1/me/password";

    private final ApiErrorWriter errorWriter;

    public ApiMustChangePasswordFilter(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && !isExempt(request.getMethod(), request.getRequestURI().substring(request.getContextPath().length()))) {
            errorWriter.write(response, ApiErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isExempt(String method, String path) {
        return path.startsWith(AUTH_PREFIX)
                || ("GET".equals(method) && ME_PATH.equals(path))
                || ("POST".equals(method) && CHANGE_PASSWORD_PATH.equals(path));
    }
}
