package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bộ lọc danh sách người dùng, lấy từ query string để giữ giá trị khi chuyển trang.
 * status: ACTIVE, LOCKED hoặc rỗng (tất cả).
 */
@Getter
@Setter
@NoArgsConstructor
public class UserFilter {

    private String keyword;
    private String role;
    private Long departmentId;
    private Long classId;
    private String status;
}
