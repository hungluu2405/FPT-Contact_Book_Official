package vn.edu.fpt.contactbook.dto;

/**
 * Một vai trò của người dùng, kèm bộ môn nếu vai trò có phạm vi bộ môn.
 */
public record RoleAssignmentView(String roleCode, String departmentCode, String departmentName) {
}
