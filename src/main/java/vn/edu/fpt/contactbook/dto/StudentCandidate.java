package vn.edu.fpt.contactbook.dto;

/**
 * Sinh viên có thể ghi danh vào lớp học phần (đang học, chưa có trong lớp hoặc đã bị gỡ).
 */
public record StudentCandidate(Long studentId, String studentCode, String fullName, String classCode) {
}
