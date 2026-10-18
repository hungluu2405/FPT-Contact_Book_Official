package vn.edu.fpt.contactbook.dto;

/**
 * Dữ liệu lịch kèm học kỳ đã chọn (học kỳ hiện tại nếu người dùng không chọn), để trang lịch không phải tra lại
 * học kỳ.
 */
public record InSemester<T>(SemesterView semester, T data) {
}
