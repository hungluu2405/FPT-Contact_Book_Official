package vn.edu.fpt.contactbook.dto;

/**
 * Kết quả ghi danh cả lớp hành chính: số SV thêm mới, ghi danh lại (từ DROPPED), bỏ qua vì đã có trong lớp,
 * bỏ qua vì không ở trạng thái đang học.
 */
public record EnrollClassResult(int added, int reEnrolled, int skippedEnrolled, int skippedNotStudying) {
}
