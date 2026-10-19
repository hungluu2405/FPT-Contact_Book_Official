package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Thống kê điểm của một nhóm sinh viên (một lớp học phần hoặc một môn): chỉ sinh viên đủ điểm mới vào phổ điểm,
 * tỷ lệ đạt và điểm trung bình; incomplete là số sinh viên chưa đủ điểm. distribution có đúng BANDS.size() phần tử,
 * theo thứ tự BANDS. mean (thang 10) và passRate (%) là null khi không ai đủ điểm.
 */
public record GradeStats(long students, long incomplete, BigDecimal mean, long passed, BigDecimal passRate,
                         List<Long> distribution) {

    /** Nhãn các khoảng của phổ điểm; biên dưới thuộc khoảng (4 thuộc "4 – < 5"). */
    public static final List<String> BANDS = List.of("< 4", "4 – < 5", "5 – < 6,5", "6,5 – < 8", "8 – < 9", "≥ 9");

    public long complete() {
        return students - incomplete;
    }
}
