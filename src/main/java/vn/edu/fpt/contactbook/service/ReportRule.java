package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.dto.GradeStats;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Luật thuần của báo cáo (SRS FR-REPORT-01, 02). Trung bình môn của từng sinh viên lấy từ GradeRule.average, chuyên
 * cần từ AttendanceRule.summarize; ở đây chỉ gộp các kết quả đó: phổ điểm 6 khoảng &lt;4, 4–&lt;5, 5–&lt;6.5,
 * 6.5–&lt;8, 8–&lt;9, ≥9 (biên dưới thuộc khoảng), đạt khi trung bình ≥ 5.0, điểm trung bình nhóm = trung bình cộng
 * các trung bình môn, làm tròn HALF_UP 2 chữ số. Sinh viên chưa đủ điểm (trung bình null) chỉ được đếm riêng.
 */
final class ReportRule {

    static final BigDecimal PASS = new BigDecimal("5.0");
    /** Biên dưới của khoảng thứ 1..5 trong GradeStats.BANDS (khoảng 0 là dưới 4). */
    private static final List<BigDecimal> LOWER_BOUNDS = List.of(new BigDecimal("4"), new BigDecimal("5"),
            new BigDecimal("6.5"), new BigDecimal("8"), new BigDecimal("9"));

    private ReportRule() {
    }

    /** averages: trung bình môn của từng sinh viên (null là chưa đủ điểm). */
    static GradeStats gradeStats(List<BigDecimal> averages) {
        List<Long> distribution = new ArrayList<>(Collections.nCopies(GradeStats.BANDS.size(), 0L));
        BigDecimal sum = BigDecimal.ZERO;
        long complete = 0;
        long passed = 0;
        for (BigDecimal average : averages) {
            if (average == null) {
                continue;
            }
            complete++;
            sum = sum.add(average);
            if (average.compareTo(PASS) >= 0) {
                passed++;
            }
            int band = band(average);
            distribution.set(band, distribution.get(band) + 1);
        }
        BigDecimal mean = complete == 0 ? null
                : sum.divide(BigDecimal.valueOf(complete), 2, RoundingMode.HALF_UP);
        BigDecimal passRate = complete == 0 ? null
                : BigDecimal.valueOf(passed * 100).divide(BigDecimal.valueOf(complete), 1, RoundingMode.HALF_UP);
        return new GradeStats(averages.size(), averages.size() - complete, mean, passed, passRate,
                List.copyOf(distribution));
    }

    /** Cộng số buổi theo trạng thái của nhiều sinh viên (để tính chuyên cần của cả lớp bằng AttendanceRule). */
    static Map<AttendanceStatus, Long> sum(Collection<Map<AttendanceStatus, Long>> counts) {
        Map<AttendanceStatus, Long> total = new EnumMap<>(AttendanceStatus.class);
        for (Map<AttendanceStatus, Long> one : counts) {
            one.forEach((status, count) -> total.merge(status, count, Long::sum));
        }
        return total;
    }

    private static int band(BigDecimal average) {
        int band = 0;
        while (band < LOWER_BOUNDS.size() && average.compareTo(LOWER_BOUNDS.get(band)) >= 0) {
            band++;
        }
        return band;
    }
}
