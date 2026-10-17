package vn.edu.fpt.contactbook.dto;

/**
 * Ảnh đại diện tải lên, tách khỏi MultipartFile để tầng service không phụ thuộc kiểu web.
 */
public record AvatarUpload(String contentType, byte[] content) {
}
