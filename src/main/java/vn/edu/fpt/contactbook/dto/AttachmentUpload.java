package vn.edu.fpt.contactbook.dto;

/**
 * Tệp đính kèm tải lên, tách khỏi MultipartFile để tầng service không phụ thuộc kiểu web.
 * fileName là tên gốc do trình duyệt gửi (chỉ để hiển thị và đặt tên khi tải về).
 */
public record AttachmentUpload(String fileName, String contentType, byte[] content) {
}
