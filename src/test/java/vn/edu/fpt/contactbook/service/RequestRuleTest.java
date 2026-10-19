package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày của đơn nghỉ: bắt buộc, đến ngày ≥ từ ngày, từ ngày không sớm hơn hôm nay − 14, tối đa 30 ngày tính cả hai đầu.
 */
class RequestRuleTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private static Map<String, String> validate(LocalDate from, LocalDate to) {
        Map<String, String> errors = new LinkedHashMap<>();
        RequestRule.validateLeaveDates(from, to, TODAY, errors);
        return errors;
    }

    @Test
    @DisplayName("Hôm nay, một ngày: hợp lệ")
    void sameDay() {
        assertThat(validate(TODAY, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("Từ ngày đúng 14 ngày trước: hợp lệ; 15 ngày trước: lỗi ở ô từ ngày")
    void pastThreshold() {
        assertThat(validate(TODAY.minusDays(14), TODAY.minusDays(14))).isEmpty();
        assertThat(validate(TODAY.minusDays(15), TODAY.minusDays(15)))
                .containsExactly(Map.entry("fromDate", RequestMessages.FROM_TOO_OLD));
    }

    @Test
    @DisplayName("Khoảng 30 ngày tính cả hai đầu: hợp lệ; 31 ngày: lỗi ở ô đến ngày")
    void spanThreshold() {
        assertThat(validate(TODAY, TODAY.plusDays(29))).isEmpty();
        assertThat(validate(TODAY, TODAY.plusDays(30)))
                .containsExactly(Map.entry("toDate", RequestMessages.SPAN_TOO_LONG));
    }

    @Test
    @DisplayName("Ngày tương lai xa vẫn hợp lệ (không giới hạn)")
    void farFuture() {
        assertThat(validate(TODAY.plusDays(200), TODAY.plusDays(201))).isEmpty();
    }

    @Test
    @DisplayName("Đến ngày trước từ ngày: lỗi ở ô đến ngày")
    void toBeforeFrom() {
        assertThat(validate(TODAY, TODAY.minusDays(1)))
                .containsExactly(Map.entry("toDate", RequestMessages.TO_BEFORE_FROM));
    }

    @Test
    @DisplayName("Thiếu ngày: lỗi ở từng ô, không kiểm tra tiếp")
    void missingDates() {
        assertThat(validate(null, null)).containsExactly(Map.entry("fromDate", RequestMessages.FROM_REQUIRED),
                Map.entry("toDate", RequestMessages.TO_REQUIRED));
        assertThat(validate(TODAY, null)).containsExactly(Map.entry("toDate", RequestMessages.TO_REQUIRED));
    }

    @Test
    @DisplayName("Tiêu đề tự điền theo dd/MM/yyyy")
    void leaveTitle() {
        assertThat(RequestRule.leaveTitle(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 8)))
                .isEqualTo("Đơn xin nghỉ học từ 07/10/2026 đến 08/10/2026");
    }
}
