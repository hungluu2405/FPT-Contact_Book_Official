package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bộ lọc danh sách danh mục, lấy từ query string để giữ giá trị khi chuyển trang.
 * groupId: khoa (bộ môn, ngành), ngành (lớp) hoặc bộ môn (môn học); null là tất cả.
 */
@Getter
@Setter
@NoArgsConstructor
public class CatalogFilter {

    private String keyword;
    private Long groupId;
}
