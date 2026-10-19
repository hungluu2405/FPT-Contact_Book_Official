package vn.edu.fpt.contactbook.dto;

/**
 * File báo cáo xuất ra (.xlsx), tạo trong bộ nhớ; fileName chỉ gồm chữ không dấu, số, gạch nối, gạch dưới, chấm.
 */
public record ReportFile(String fileName, byte[] content) {
}
