package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.fpt.contactbook.dto.GradeStats;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thống kê điểm của báo cáo: phổ điểm 6 khoảng (biên dưới thuộc khoảng trên), tỷ lệ đạt (≥ 5.0), điểm trung bình
 * lớp; sinh viên chưa đủ điểm (trung bình null) chỉ được đếm riêng.
 */
class ReportRuleTest {

    @ParameterizedTest(name = "{0} → khoảng {1}")
    @CsvSource({
            "0.0, 0", "3.9, 0",
            "4.0, 1", "4.9, 1",
            "5.0, 2", "6.4, 2",
            "6.5, 3", "7.9, 3",
            "8.0, 4", "8.9, 4",
            "9.0, 5", "10.0, 5"})
    @DisplayName("Mỗi điểm rơi đúng một khoảng; điểm bằng biên dưới thuộc khoảng trên")
    void bandBoundaries(String average, int band) {
        GradeStats stats = ReportRule.gradeStats(List.of(new BigDecimal(average)));

        List<Long> expected = new ArrayList<>(List.of(0L, 0L, 0L, 0L, 0L, 0L));
        expected.set(band, 1L);
        assertThat(stats.distribution()).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("Đạt khi trung bình ≥ 5.0: 4.9 trượt, 5.0 đạt")
    void passBoundary() {
        GradeStats stats = ReportRule.gradeStats(averages("4.9", "5.0"));

        assertThat(stats.passed()).isEqualTo(1);
        assertThat(stats.passRate()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("Sinh viên chưa đủ điểm không vào phổ điểm, tỷ lệ đạt hay trung bình, chỉ đếm riêng")
    void incompleteExcluded() {
        GradeStats stats = ReportRule.gradeStats(averages("8.0", null, "6.0", null));

        assertThat(stats.students()).isEqualTo(4);
        assertThat(stats.incomplete()).isEqualTo(2);
        assertThat(stats.complete()).isEqualTo(2);
        assertThat(stats.distribution()).containsExactly(0L, 0L, 1L, 0L, 1L, 0L);
        assertThat(stats.mean()).isEqualByComparingTo("7.00");
        assertThat(stats.passRate()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("Trung bình lớp làm tròn HALF_UP 2 chữ số; tỷ lệ đạt 1 chữ số")
    void rounding() {
        GradeStats stats = ReportRule.gradeStats(averages("8.1", "7.0", "4.0"));

        assertThat(stats.mean()).isEqualTo(new BigDecimal("6.37"));
        assertThat(stats.passRate()).isEqualTo(new BigDecimal("66.7"));
    }

    @Test
    @DisplayName("Không ai đủ điểm (hoặc lớp rỗng): trung bình và tỷ lệ đạt là null, phổ điểm toàn 0")
    void nothingComplete() {
        GradeStats none = ReportRule.gradeStats(averages(null, null));
        GradeStats empty = ReportRule.gradeStats(List.of());

        for (GradeStats stats : List.of(none, empty)) {
            assertThat(stats.mean()).isNull();
            assertThat(stats.passRate()).isNull();
            assertThat(stats.passed()).isZero();
            assertThat(stats.distribution()).containsOnly(0L).hasSize(GradeStats.BANDS.size());
        }
        assertThat(none.incomplete()).isEqualTo(2);
        assertThat(empty.students()).isZero();
    }

    @Test
    @DisplayName("Cộng số buổi theo trạng thái của nhiều sinh viên; trạng thái thiếu coi là 0")
    void sumCounts() {
        Map<AttendanceStatus, Long> a = new EnumMap<>(Map.of(AttendanceStatus.PRESENT, 3L, AttendanceStatus.ABSENT_EXCUSED, 1L));
        Map<AttendanceStatus, Long> b = Map.of(AttendanceStatus.PRESENT, 2L, AttendanceStatus.ABSENT_UNEXCUSED, 1L);

        assertThat(ReportRule.sum(List.of(a, b))).containsExactlyInAnyOrderEntriesOf(Map.of(
                AttendanceStatus.PRESENT, 5L, AttendanceStatus.ABSENT_EXCUSED, 1L, AttendanceStatus.ABSENT_UNEXCUSED, 1L));
        assertThat(ReportRule.sum(List.of())).isEmpty();
    }

    private static List<BigDecimal> averages(String... values) {
        return Arrays.stream(values).map(v -> v == null ? null : new BigDecimal(v)).toList();
    }
}
