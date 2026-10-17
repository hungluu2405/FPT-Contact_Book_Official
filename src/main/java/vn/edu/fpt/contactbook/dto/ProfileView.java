package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileView(String phone, String fullName, String email, Gender gender, LocalDate dateOfBirth,
                          String address, String avatarUrl, LocalDateTime lastLoginAt,
                          List<RoleAssignmentView> roles) {
}
