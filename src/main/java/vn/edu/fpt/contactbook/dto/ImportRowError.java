package vn.edu.fpt.contactbook.dto;

/**
 * Một lỗi khi nhập Excel. rowNumber là số dòng người dùng thấy trong Excel (dòng tiêu đề là 1);
 * column là tiêu đề cột, rỗng nếu lỗi không gắn với cột nào.
 */
public record ImportRowError(int rowNumber, String column, String message) {
}
