package vn.edu.fpt.contactbook.security;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Đếm số lần đăng nhập sai theo số điện thoại đã chuẩn hóa (kể cả số không tồn tại) và khóa tạm.
 * Lưu trong bộ nhớ: khởi động lại ứng dụng thì bộ đếm mất (docs/ASSUMPTIONS.md).
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** Lần sai cách lần sai trước quá khoảng này thì bắt đầu đếm lại. */
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String phone) {
        Attempt attempt = attempts.get(phone);
        return attempt != null && attempt.lockedAt(clock.instant());
    }

    /** Ghi một lần sai; trả true nếu chính lần này làm số điện thoại bị khóa tạm (để ghi LOGIN_LOCKED một lần). */
    public boolean recordFailure(String phone) {
        Instant now = clock.instant();
        Attempt attempt = attempts.compute(phone, (key, previous) -> {
            int failures = previous == null || previous.expiredAt(now) ? 1 : previous.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
            return new Attempt(failures, now, lockedUntil);
        });
        return attempt.failures() == MAX_FAILURES;
    }

    public void reset(String phone) {
        attempts.remove(phone);
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant now = clock.instant();
        attempts.values().removeIf(attempt -> attempt.expiredAt(now));
    }

    /** Số mục đang lưu, để kiểm tra việc dọn bộ nhớ. */
    int trackedCount() {
        return attempts.size();
    }

    private record Attempt(int failures, Instant lastFailure, Instant lockedUntil) {

        boolean lockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean expiredAt(Instant now) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(lastFailure.plus(FAILURE_WINDOW));
        }
    }
}
