package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.api.ChangePasswordRequest;
import vn.edu.fpt.contactbook.dto.api.MeResponse;
import vn.edu.fpt.contactbook.dto.api.TokenResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ApiAuthService;

@Tag(name = "Me", description = "Tài khoản đang đăng nhập")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeApiController {

    private final ApiAuthService apiAuthService;

    @Operation(summary = "Thông tin người dùng và vai trò")
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return MeResponse.from(principal);
    }

    @Operation(summary = "Đổi mật khẩu, trả cặp token mới (refresh token cũ hết hiệu lực)")
    @PostMapping("/password")
    public TokenResponse changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @Valid @RequestBody ChangePasswordRequest request,
                                        HttpServletRequest httpRequest) {
        return apiAuthService.changePassword(principal.getUserId(), request.currentPassword(),
                request.newPassword(), request.confirmPassword(), httpRequest.getRemoteAddr());
    }
}
