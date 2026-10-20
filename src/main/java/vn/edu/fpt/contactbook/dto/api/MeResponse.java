package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;

/** Người dùng đang đăng nhập; roles sắp theo tên (mã trong bảng roles). */
public record MeResponse(Long id, String phone, String fullName, List<String> roles, boolean mustChangePassword) {

    public static MeResponse from(AppUserPrincipal principal) {
        return new MeResponse(principal.getUserId(), principal.getPhone(), principal.getFullName(),
                principal.getRoles().stream().sorted().toList(), principal.isMustChangePassword());
    }
}
