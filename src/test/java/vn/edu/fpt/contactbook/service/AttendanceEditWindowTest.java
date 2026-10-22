package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceEditWindowTest {

    final AttendanceEditWindow window = new AttendanceEditWindow(24);

    @Test
    @DisplayName("Đã điểm danh: hạn = lần điểm danh đầu + 24 giờ (điểm danh 8:00 ngày 22 → 8:00 ngày 23)")
    void deadlineFromFirstMark() {
        LocalDateTime marked = LocalDateTime.of(2026, 10, 22, 8, 0);

        assertThat(window.deadline(marked, LocalDate.of(2026, 10, 22), LocalTime.of(9, 45)))
                .isEqualTo(LocalDateTime.of(2026, 10, 23, 8, 0));
    }

    @Test
    @DisplayName("Chưa điểm danh: hạn = giờ kết thúc buổi + 24 giờ")
    void deadlineFromSessionEnd() {
        assertThat(window.deadline(null, LocalDate.of(2026, 10, 22), LocalTime.of(9, 45)))
                .isEqualTo(LocalDateTime.of(2026, 10, 23, 9, 45));
    }

    @Test
    @DisplayName("Đúng mốc vẫn còn hạn; quá một phút thì hết hạn")
    void boundary() {
        LocalDateTime deadline = LocalDateTime.of(2026, 10, 23, 8, 0);

        assertThat(window.isOpen(deadline, deadline.minusMinutes(1))).isTrue();
        assertThat(window.isOpen(deadline, deadline)).isTrue();
        assertThat(window.isOpen(deadline, deadline.plusMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("Số giờ lấy từ cấu hình")
    void configurableHours() {
        assertThat(new AttendanceEditWindow(48).deadline(LocalDateTime.of(2026, 10, 22, 8, 0), null, null))
                .isEqualTo(LocalDateTime.of(2026, 10, 24, 8, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("Số giờ không dương bị từ chối khi khởi động")
    void rejectsNonPositive(int hours) {
        assertThatThrownBy(() -> new AttendanceEditWindow(hours)).isInstanceOf(IllegalArgumentException.class);
    }
}
