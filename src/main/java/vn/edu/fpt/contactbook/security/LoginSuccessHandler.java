package vn.edu.fpt.contactbook.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.service.AccountService;

import java.io.IOException;
import java.util.Optional;

/**
 * Chỉ lấy IP rồi giao cho AccountService ghi last_login_at và audit LOGIN; sau đó chuyển về trang chủ
 * theo vai trò. Người dùng không có vai trò nào bị đăng xuất ngay.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accountService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        Optional<String> home = RoleHomeResolver.homeFor(principal.getRoles());
        if (home.isEmpty()) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            redirectStrategy.sendRedirect(request, response, "/login?error=norole");
            return;
        }
        accountService.recordLogin(principal.getUserId(), request.getRemoteAddr());
        redirectStrategy.sendRedirect(request, response, home.get());
    }
}
