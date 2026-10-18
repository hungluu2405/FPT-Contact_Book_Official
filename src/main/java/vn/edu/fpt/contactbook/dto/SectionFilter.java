package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;

/**
 * Bộ lọc danh sách lớp học phần, lấy từ query string để giữ giá trị khi chuyển trang; null là tất cả.
 */
@Getter
@Setter
@NoArgsConstructor
public class SectionFilter {

    private String keyword;
    private Long semesterId;
    private SectionStatus status;
}
