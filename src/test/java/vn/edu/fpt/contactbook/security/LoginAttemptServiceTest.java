package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String PHONE = "0900000001";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-17T08:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    @Test
    @DisplayName("4 lần sai chưa khóa, lần thứ 5 thì khóa")
    void locksAfterFiveFailures() {
        failTimes(4);
        assertThat(service.isLocked(PHONE)).isFalse();

        service.recordFailure(PHONE);
        assertThat(service.isLocked(PHONE)).isTrue();
    }

    @Test
    @DisplayName("Khóa đúng 15 phút rồi tự mở, đếm lại từ đầu")
    void lockExpiresAfterFifteenMinutes() {
        failTimes(5);

        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(1));
        assertThat(service.isLocked(PHONE)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isLocked(PHONE)).isFalse();

        service.recordFailure(PHONE);
        assertThat(service.isLocked(PHONE)).as("sau khi hết khóa, một lần sai không khóa lại").isFalse();
    }

    @Test
    @DisplayName("Đăng nhập đúng xóa bộ đếm")
    void resetClearsCounter() {
        failTimes(4);
        service.reset(PHONE);
        failTimes(4);

        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    @DisplayName("Reset gỡ cả trạng thái đang khóa")
    void resetUnlocks() {
        failTimes(5);
        service.reset(PHONE);

        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    @DisplayName("Lần sai cách nhau quá 15 phút thì không cộng dồn")
    void failuresOutsideWindowStartOver() {
        failTimes(4);
        clock.advance(LoginAttemptService.FAILURE_WINDOW);

        service.recordFailure(PHONE);
        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    @DisplayName("Mỗi số đếm riêng")
    void countersArePerPhone() {
        failTimes(5);

        assertThat(service.isLocked("0900000002")).isFalse();
    }

    @Test
    @DisplayName("Dọn các mục hết hạn, giữ mục còn hiệu lực")
    void evictsExpiredEntries() {
        failTimes(5);
        service.recordFailure("0900000002");
        clock.advance(Duration.ofMinutes(10));
        service.recordFailure("0900000003");

        clock.advance(Duration.ofMinutes(6));
        service.evictExpired();

        assertThat(service.trackedCount()).isEqualTo(1);
        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    @DisplayName("Đếm đúng khi nhiều luồng cùng ghi lần sai")
    void countsConcurrentFailures() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            pool.submit(() -> service.recordFailure(PHONE));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(service.isLocked(PHONE)).isTrue();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(PHONE);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
