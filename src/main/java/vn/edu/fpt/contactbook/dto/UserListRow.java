package vn.edu.fpt.contactbook.dto;

/**
 * Một dòng của danh sách người dùng; vai trò được nạp riêng một lần cho cả trang.
 */
public record UserListRow(Long id, String phone, String fullName, String email, boolean active,
                          String studentCode, String teacherCode, String departmentName) {
}
