package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Bộ lọc trang nhật ký hệ thống; trống là không lọc. keyword tìm trong họ tên hoặc số điện thoại người thực hiện;
 * from, to là ngày theo giờ Asia/Ho_Chi_Minh, tính cả hai đầu.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuditLogFilter {

    private String keyword;
    private String action;
    private String entityType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
