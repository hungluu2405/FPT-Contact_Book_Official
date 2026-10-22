package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Công thức tỷ lệ vắng, tỷ lệ chuyên cần và ngưỡng cảnh báo (ngưỡng 20%, cảnh báo khi VƯỢT ngưỡng).
 */
class AttendanceRuleTest {

    private final AttendanceRule rule = new AttendanceRule(20);

    @Test
    @DisplayName("Vắng đúng 20% (2/10 buổi) chưa cảnh báo")
    void exactlyAtThresholdIsNotWarning() {
        AttendanceSummary summary = rule.summarize(Map.of(AttendanceStatus.ABSENT_UNEXCUSED, 2L,
                AttendanceStatus.PRESENT, 3L), 10);

        assertThat(summary.absentRate()).isEqualByComparingTo("20.0");
        assertThat(summary.warning()).isFalse();
    }

    @Test
    @DisplayName("Vắng 30% (3/10 buổi, tính cả vắng có phép) thì cảnh báo")
    void aboveThresholdIsWarning() {
        AttendanceSummary summary = rule.summarize(Map.of(AttendanceStatus.ABSENT_EXCUSED, 2L,
                AttendanceStatus.ABSENT_UNEXCUSED, 1L), 10);

        assertThat(summary.absent()).isEqualTo(3);
        assertThat(summary.absentRate()).isEqualByComparingTo("30.0");
        assertThat(summary.warning()).isTrue();
    }

    @ParameterizedTest(name = "vắng {0}/{1} buổi → cảnh báo = {2}")
    @CsvSource({"3, 15, false", "4, 15, true", "1, 6, false", "2, 7, true", "1, 4, true", "0, 1, false"})
    @DisplayName("So sánh với ngưỡng bằng số nguyên, không lệch do làm tròn")
    void comparesWithoutRounding(long absent, long total, boolean warning) {
        assertThat(rule.summarize(Map.of(AttendanceStatus.ABSENT_UNEXCUSED, absent), total).warning())
                .isEqualTo(warning);
    }

    @Test
    @DisplayName("Lớp chưa có buổi nào: tỷ lệ vắng 0%, không cảnh báo, chuyên cần chưa có")
    void noSessions() {
        AttendanceSummary summary = rule.summarize(Map.of(), 0);

        assertThat(summary.totalSessions()).isZero();
        assertThat(summary.absentRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.attendanceRate()).isNull();
        assertThat(summary.warning()).isFalse();
    }

    @Test
    @DisplayName("Chỉ có mặt (đi muộn cũng điểm danh là có mặt): không vắng, chuyên cần 100%")
    void presentOnlyIsNotAbsent() {
        AttendanceSummary summary = rule.summarize(Map.of(AttendanceStatus.PRESENT, 5L), 10);

        assertThat(summary.absentRate()).isEqualByComparingTo("0.0");
        assertThat(summary.warning()).isFalse();
        assertThat(summary.attendanceRate()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("Chuyên cần = có mặt / số buổi đã điểm danh, làm tròn 1 chữ số")
    void attendanceRateUsesMarkedSessions() {
        AttendanceSummary summary = rule.summarize(Map.of(AttendanceStatus.PRESENT, 2L,
                AttendanceStatus.ABSENT_EXCUSED, 1L), 12);

        assertThat(summary.markedSessions()).isEqualTo(3);
        assertThat(summary.attendanceRate()).isEqualByComparingTo("66.7");
        assertThat(summary.absentRate()).isEqualByComparingTo("8.3");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    @DisplayName("Ngưỡng ngoài 0..100 bị từ chối khi khởi động")
    void rejectsInvalidThreshold(int threshold) {
        assertThatThrownBy(() -> new AttendanceRule(threshold)).isInstanceOf(IllegalArgumentException.class);
    }
}
