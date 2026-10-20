package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.api.ChangePasswordRequest;
import vn.edu.fpt.contactbook.dto.api.MeResponse;
import vn.edu.fpt.contactbook.dto.api.ProfileResponse;
import vn.edu.fpt.contactbook.dto.api.TokenResponse;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ApiAuthService;
import vn.edu.fpt.contactbook.service.AvatarStorage;
import vn.edu.fpt.contactbook.service.ProfileService;

import java.time.Duration;

@Tag(name = "Me", description = "Tài khoản đang đăng nhập")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeApiController {

    private final ApiAuthService apiAuthService;
    private final ProfileService profileService;
    private final AvatarStorage avatarStorage;

    @Operation(summary = "Thông tin người dùng và vai trò")
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return MeResponse.from(principal);
    }

    @Operation(summary = "Hồ sơ của chính mình")
    @GetMapping("/profile")
    public ProfileResponse profile(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ProfileResponse.from(profileService.getProfile(principal.getUserId()));
    }

    @Operation(summary = "Ảnh đại diện của chính mình (404 nếu chưa có)")
    @GetMapping("/avatar")
    public ResponseEntity<Resource> avatar(@AuthenticationPrincipal AppUserPrincipal principal) {
        return AvatarStorage.fileNameOf(profileService.getProfile(principal.getUserId()).avatarUrl())
                .flatMap(fileName -> avatarStorage.resolve(fileName)
                        .map(file -> ResponseEntity.ok()
                                .contentType(fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                                .<Resource>body(new FileSystemResource(file))))
                .orElseThrow(() -> new NotFoundException("Chưa có ảnh đại diện"));
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
