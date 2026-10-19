package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;

/**
 * Bộ lọc danh sách câu lạc bộ (query string): từ khóa theo mã hoặc tên, trạng thái (null là tất cả).
 */
@Getter
@Setter
@NoArgsConstructor
public class ClubFilter {

    private String keyword;
    private ClubStatus status;
}
