package vn.edu.fpt.contactbook.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Quy tắc ngày của đơn nghỉ: từ ngày, đến ngày bắt buộc; đến ngày ≥ từ ngày; từ ngày không sớm hơn hôm nay − 14 ngày
 * (đúng 14 ngày trước vẫn hợp lệ); khoảng nghỉ tính cả hai đầu tối đa 30 ngày. Ngày tương lai không giới hạn.
 */
final class RequestRule {

    static final int MAX_PAST_DAYS = 14;
    static final int MAX_SPAN_DAYS = 30;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RequestRule() {
    }

    /** Ghi lỗi theo ô (fromDate, toDate) vào errors; today là hôm nay theo Asia/Ho_Chi_Minh. */
    static void validateLeaveDates(LocalDate from, LocalDate to, LocalDate today, Map<String, String> errors) {
        if (from == null) {
            errors.putIfAbsent("fromDate", RequestMessages.FROM_REQUIRED);
        }
        if (to == null) {
            errors.putIfAbsent("toDate", RequestMessages.TO_REQUIRED);
        }
        if (from == null || to == null) {
            return;
        }
        if (from.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            errors.putIfAbsent("fromDate", RequestMessages.FROM_TOO_OLD);
        }
        if (to.isBefore(from)) {
            errors.putIfAbsent("toDate", RequestMessages.TO_BEFORE_FROM);
        } else if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_SPAN_DAYS) {
            errors.putIfAbsent("toDate", RequestMessages.SPAN_TOO_LONG);
        }
    }

    /** Tiêu đề tự điền khi người gửi để trống. */
    static String leaveTitle(LocalDate from, LocalDate to) {
        return "Đơn xin nghỉ học từ " + from.format(DATE) + " đến " + to.format(DATE);
    }
}
