package vn.edu.fpt.contactbook.security;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Giới hạn quên mật khẩu theo số điện thoại (kể cả số không tồn tại, để không lộ tài khoản): gửi mã cách nhau ít nhất
 * 1 phút và tối đa 5 lần mỗi giờ; nhập sai mã 5 lần thì khóa 15 phút. Lưu trong bộ nhớ như LoginAttemptService.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetAttemptService {

    static final Duration SEND_INTERVAL = Duration.ofMinutes(1);
    static final Duration SEND_WINDOW = Duration.ofHours(1);
    static final int MAX_SENDS_PER_WINDOW = 5;
    static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> sends = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Failures> failures = new ConcurrentHashMap<>();

    /** Ghi một lần gửi mã nếu còn được phép; trả false khi phải chờ. */
    public boolean tryAcquireSend(String phone) {
        Instant now = clock.instant();
        boolean[] allowed = {false};
        sends.compute(phone, (key, previous) -> {
            Deque<Instant> history = previous == null ? new ArrayDeque<>() : previous;
            history.removeIf(sentAt -> !now.isBefore(sentAt.plus(SEND_WINDOW)));
            Instant last = history.peekLast();
            if ((last == null || !now.isBefore(last.plus(SEND_INTERVAL))) && history.size() < MAX_SENDS_PER_WINDOW) {
                history.addLast(now);
                allowed[0] = true;
            }
            return history;
        });
        return allowed[0];
    }

    public boolean isLocked(String phone) {
        Failures entry = failures.get(phone);
        return entry != null && entry.lockedAt(clock.instant());
    }

    /** Ghi một lần nhập sai mã; trả true nếu chính lần này làm số điện thoại bị khóa. */
    public boolean recordFailure(String phone) {
        Instant now = clock.instant();
        Failures entry = failures.compute(phone, (key, previous) -> {
            int count = previous == null || previous.expiredAt(now) ? 1 : previous.count() + 1;
            return new Failures(count, now, count >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null);
        });
        return entry.count() == MAX_FAILURES;
    }

    /** Đặt lại mật khẩu thành công: xóa bộ đếm nhập sai và lịch sử gửi mã của số điện thoại. */
    public void reset(String phone) {
        failures.remove(phone);
        sends.remove(phone);
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant now = clock.instant();
        failures.values().removeIf(entry -> entry.expiredAt(now));
        sends.values().removeIf(history -> history.isEmpty()
                || !now.isBefore(history.peekLast().plus(SEND_WINDOW)));
    }

    /** Số mục đang lưu, để kiểm tra việc dọn bộ nhớ. */
    int trackedCount() {
        return sends.size() + failures.size();
    }

    private record Failures(int count, Instant lastFailure, Instant lockedUntil) {

        boolean lockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean expiredAt(Instant now) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(lastFailure.plus(LOCK_DURATION));
        }
    }
}
