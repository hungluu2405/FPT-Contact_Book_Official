package vn.edu.fpt.contactbook.dto;

import java.util.List;

public record UserSummaryView(UserListRow user, List<RoleAssignmentView> roles) {
}
