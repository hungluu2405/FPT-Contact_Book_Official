package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.ProfileView;
import vn.edu.fpt.contactbook.dto.RoleAssignmentView;
import vn.edu.fpt.contactbook.model.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Hồ sơ của chính mình; ảnh đại diện tải qua GET /api/v1/me/avatar khi hasAvatar = true. */
public record ProfileResponse(String phone, String fullName, String email, Gender gender, LocalDate dateOfBirth,
                              String address, boolean hasAvatar, LocalDateTime lastLoginAt,
                              List<RoleAssignmentView> roles) {

    public static ProfileResponse from(ProfileView profile) {
        return new ProfileResponse(profile.phone(), profile.fullName(), profile.email(), profile.gender(),
                profile.dateOfBirth(), profile.address(), profile.avatarUrl() != null, profile.lastLoginAt(),
                profile.roles());
    }
}
