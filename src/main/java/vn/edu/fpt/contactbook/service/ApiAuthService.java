package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import vn.edu.fpt.contactbook.dto.api.MeResponse;
import vn.edu.fpt.contactbook.dto.api.TokenResponse;
import vn.edu.fpt.contactbook.exception.ApiAuthException;
import vn.edu.fpt.contactbook.exception.ApiAuthException.Reason;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.security.JwtTokenService;
import vn.edu.fpt.contactbook.security.PhoneAuthenticationProvider;
import vn.edu.fpt.contactbook.security.RoleHomeResolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Đăng nhập, làm mới token và đổi mật khẩu của REST API. Đăng nhập đi qua đúng PhoneAuthenticationProvider của web
 * (chuẩn hóa số điện thoại, khóa tạm, audit LOGIN_FAILED/LOGIN_LOCKED); BadCredentials, Locked, Disabled được ném
 * tiếp để ApiExceptionHandler đổi thành JSON.
 */
@Service
@RequiredArgsConstructor
public class ApiAuthService {

    private final PhoneAuthenticationProvider authenticationProvider;
    private final AppUserDetailsService userDetailsService;
    private final AccountService accountService;
    private final JwtTokenService jwtTokenService;

    public TokenResponse login(String phone, String password, String ipAddress) {
        UsernamePasswordAuthenticationToken request = UsernamePasswordAuthenticationToken.unauthenticated(phone,
                password);
        request.setDetails(new WebAuthenticationDetails(ipAddress, null));
        AppUserPrincipal principal = (AppUserPrincipal) authenticationProvider.authenticate(request).getPrincipal();
        requireRole(principal);
        accountService.recordLogin(principal.getUserId(), ipAddress);
        return tokens(principal);
    }

    /**
     * Refresh token hợp lệ, tài khoản còn hoạt động và mật khẩu chưa đổi kể từ lúc phát hành: trả cặp token mới.
     * Sai một trong các điều kiện: 401 (tài khoản bị khóa: 403 ACCOUNT_DISABLED).
     */
    public TokenResponse refresh(String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtTokenService.decodeRefresh(refreshToken);
        } catch (JwtException e) {
            throw new ApiAuthException(Reason.INVALID_REFRESH_TOKEN);
        }
        AppUserPrincipal principal = loadPrincipal(jwt.getSubject());
        if (!principal.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
        String fingerprint = jwt.getClaimAsString(JwtTokenService.CLAIM_PASSWORD_FINGERPRINT);
        if (fingerprint == null || !MessageDigest.isEqual(
                fingerprint.getBytes(StandardCharsets.UTF_8),
                JwtTokenService.passwordFingerprint(principal.getPassword()).getBytes(StandardCharsets.UTF_8))) {
            throw new ApiAuthException(Reason.INVALID_REFRESH_TOKEN);
        }
        requireRole(principal);
        return tokens(principal);
    }

    /** Đổi mật khẩu (cùng quy tắc, audit với web) rồi phát cặp token mới; refresh token cũ hết hiệu lực. */
    public TokenResponse changePassword(Long userId, String currentPassword, String newPassword,
                                        String confirmPassword, String ipAddress) {
        accountService.changePassword(userId, currentPassword, newPassword, confirmPassword, ipAddress);
        return tokens(userDetailsService.loadById(userId)
                .orElseThrow(() -> new ApiAuthException(Reason.INVALID_REFRESH_TOKEN)));
    }

    private AppUserPrincipal loadPrincipal(String subject) {
        try {
            return userDetailsService.loadById(Long.valueOf(subject))
                    .orElseThrow(() -> new ApiAuthException(Reason.INVALID_REFRESH_TOKEN));
        } catch (NumberFormatException e) {
            throw new ApiAuthException(Reason.INVALID_REFRESH_TOKEN);
        }
    }

    /** Như web (LoginSuccessHandler): tài khoản chưa có vai trò nào không được đăng nhập. */
    private static void requireRole(AppUserPrincipal principal) {
        if (RoleHomeResolver.homeFor(principal.getRoles()).isEmpty()) {
            throw new ApiAuthException(Reason.NO_ROLE);
        }
    }

    private TokenResponse tokens(AppUserPrincipal principal) {
        JwtTokenService.TokenPair pair = jwtTokenService.issue(principal.getUserId(), principal.getPassword());
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), TokenResponse.BEARER,
                pair.expiresInSeconds(), MeResponse.from(principal));
    }
}
