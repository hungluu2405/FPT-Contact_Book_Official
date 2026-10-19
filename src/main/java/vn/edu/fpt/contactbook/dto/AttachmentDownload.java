package vn.edu.fpt.contactbook.dto;

import java.nio.file.Path;

/**
 * Tệp đính kèm được phép tải: tên gốc, content type đã kiểm tra khi lưu, đường dẫn trên đĩa.
 */
public record AttachmentDownload(String fileName, String contentType, Path path) {
}
