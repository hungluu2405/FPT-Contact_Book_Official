package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.api.LoginRequest;
import vn.edu.fpt.contactbook.dto.api.RefreshRequest;
import vn.edu.fpt.contactbook.dto.api.TokenResponse;
import vn.edu.fpt.contactbook.service.ApiAuthService;

@Tag(name = "Auth", description = "Đăng nhập, làm mới token, đăng xuất")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final ApiAuthService apiAuthService;

    @Operation(summary = "Đăng nhập bằng số điện thoại")
    @SecurityRequirements
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return apiAuthService.login(request.phone(), request.password(), httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Đổi refresh token lấy cặp token mới")
    @SecurityRequirements
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return apiAuthService.refresh(request.refreshToken());
    }

    /** Server không lưu token: đăng xuất là mobile xóa token đã lưu; endpoint có để luồng gọi thống nhất. */
    @Operation(summary = "Đăng xuất (mobile xóa token đã lưu)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
