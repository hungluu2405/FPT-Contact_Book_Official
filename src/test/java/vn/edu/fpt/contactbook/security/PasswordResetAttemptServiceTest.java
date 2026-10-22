package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.contactbook.support.MutableClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetAttemptServiceTest {

    private static final String PHONE = "0900000004";
    private static final String OTHER_PHONE = "0900000005";

    private MutableClock clock;
    private PasswordResetAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-22T08:00:00Z"));
        service = new PasswordResetAttemptService(clock);
    }

    @Test
    @DisplayName("Gửi mã cách nhau ít nhất 1 phút; số điện thoại khác không bị ảnh hưởng")
    void enforcesSendInterval() {
        assertThat(service.tryAcquireSend(PHONE)).isTrue();
        assertThat(service.tryAcquireSend(PHONE)).isFalse();
        assertThat(service.tryAcquireSend(OTHER_PHONE)).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(service.tryAcquireSend(PHONE)).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.tryAcquireSend(PHONE)).isTrue();
    }

    @Test
    @DisplayName("Tối đa 5 lần gửi mỗi giờ, lần thứ 6 phải chờ lần đầu ra khỏi cửa sổ 1 giờ")
    void enforcesHourlyLimit() {
        for (int i = 1; i <= PasswordResetAttemptService.MAX_SENDS_PER_WINDOW; i++) {
            assertThat(service.tryAcquireSend(PHONE)).as("lần gửi %d", i).isTrue();
            clock.advance(Duration.ofMinutes(2));
        }
        assertThat(service.tryAcquireSend(PHONE)).isFalse();

        // Lần gửi đầu là lúc 08:00; đã qua 10 phút, cần tới 09:00.
        clock.advance(Duration.ofMinutes(49));
        assertThat(service.tryAcquireSend(PHONE)).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.tryAcquireSend(PHONE)).isTrue();
    }

    @Test
    @DisplayName("Nhập sai mã 5 lần thì khóa 15 phút; recordFailure trả true đúng ở lần khóa")
    void locksAfterFiveFailures() {
        for (int i = 1; i < PasswordResetAttemptService.MAX_FAILURES; i++) {
            assertThat(service.recordFailure(PHONE)).as("lần sai %d", i).isFalse();
        }
        assertThat(service.isLocked(PHONE)).isFalse();

        assertThat(service.recordFailure(PHONE)).isTrue();
        assertThat(service.isLocked(PHONE)).isTrue();
        assertThat(service.isLocked(OTHER_PHONE)).isFalse();

        clock.advance(PasswordResetAttemptService.LOCK_DURATION);
        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    @DisplayName("reset xóa bộ đếm nhập sai và lịch sử gửi mã")
    void resetClearsFailuresAndSends() {
        for (int i = 1; i < PasswordResetAttemptService.MAX_FAILURES; i++) {
            service.recordFailure(PHONE);
        }
        service.tryAcquireSend(PHONE);
        service.reset(PHONE);

        assertThat(service.recordFailure(PHONE)).isFalse();
        assertThat(service.isLocked(PHONE)).isFalse();
        assertThat(service.tryAcquireSend(PHONE)).isTrue();
    }

    @Test
    @DisplayName("Dọn bộ nhớ: mục đã hết hạn bị xóa, mục còn hiệu lực được giữ")
    void evictsExpiredEntries() {
        service.tryAcquireSend(PHONE);
        service.recordFailure(OTHER_PHONE);
        service.evictExpired();
        assertThat(service.trackedCount()).isEqualTo(2);

        clock.advance(Duration.ofHours(1));
        service.evictExpired();
        assertThat(service.trackedCount()).isZero();
    }
}
