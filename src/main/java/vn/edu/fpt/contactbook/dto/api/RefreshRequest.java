package vn.edu.fpt.contactbook.dto.api;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "{api.refresh.token.required}") String refreshToken) {
}
