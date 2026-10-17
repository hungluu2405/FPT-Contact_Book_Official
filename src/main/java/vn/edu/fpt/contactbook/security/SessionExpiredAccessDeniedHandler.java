package vn.edu.fpt.contactbook.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/**
 * Form để quá hạn phiên (30 phút) mang mã CSRF cũ: người dùng chưa đăng nhập thì về trang đăng nhập
 * với thông báo hết phiên, thay vì trang 403. Lỗi CSRF khi đang đăng nhập và mọi lỗi quyền khác vẫn là 403.
 */
public class SessionExpiredAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException
                && !trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication())) {
            response.sendRedirect(request.getContextPath() + "/login?error=expired");
            return;
        }
        delegate.handle(request, response, accessDeniedException);
    }
}
