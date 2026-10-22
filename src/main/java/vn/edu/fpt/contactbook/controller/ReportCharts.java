package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.AttendanceReport;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.dto.ClubReportRow;
import vn.edu.fpt.contactbook.dto.GradeReport;
import vn.edu.fpt.contactbook.dto.GradeStats;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dữ liệu biểu đồ Chart.js cho các trang báo cáo, đưa vào Model (không có endpoint JSON) và được Thymeleaf in vào
 * trang bằng JavaScript inlining (tự escape). Chỉ dùng Map, List, String, Number để serializer của Thymeleaf đọc
 * được. Dạng: {type, labels, datasets: [{label, data}], percent}; static/js/report-charts.js vẽ và tô màu.
 * null là không có biểu đồ.
 */
@Component
@RequiredArgsConstructor
class ReportCharts {

    private final MessageSource messageSource;

    /** Một lớp: số buổi theo trạng thái của cả lớp; tổng hợp: chuyên cần từng lớp của trang đang xem. */
    Map<String, Object> attendance(AttendanceReport report) {
        if (report.section() != null) {
            AttendanceSummary s = report.section().classSummary();
            if (s.markedSessions() == 0) {
                return null;
            }
            Map<AttendanceStatus, Long> counts = new LinkedHashMap<>();
            counts.put(AttendanceStatus.PRESENT, s.present());
            counts.put(AttendanceStatus.ABSENT_EXCUSED, s.absentExcused());
            counts.put(AttendanceStatus.ABSENT_UNEXCUSED, s.absentUnexcused());
            List<String> labels = counts.keySet().stream()
                    .map(status -> message("attendance-status." + status.name())).toList();
            return chart("doughnut", labels, List.of(dataset(message("report.chart.sessions"),
                    List.copyOf(counts.values()))), false);
        }
        if (report.rows().isEmpty()) {
            return null;
        }
        return chart("bar", report.rows().map(r -> r.section().code()).getContent(),
                List.of(dataset(message("attendance.rate"),
                        report.rows().map(r -> number(r.attendanceRate())).getContent())), true);
    }

    /** Một lớp hoặc một môn: phổ điểm; tổng quan: điểm trung bình từng lớp của trang đang xem. */
    Map<String, Object> grades(GradeReport report) {
        if (report.stats() != null) {
            if (report.stats().complete() == 0) {
                return null;
            }
            return chart("bar", GradeStats.BANDS, List.of(dataset(message("report.chart.students"),
                    report.stats().distribution())), false);
        }
        if (report.rows().isEmpty()) {
            return null;
        }
        return chart("bar", report.rows().map(r -> r.section().code()).getContent(),
                List.of(dataset(message("report.grades.mean"),
                        report.rows().map(r -> number(r.stats().mean())).getContent())), false);
    }

    /** Thành viên ACTIVE và tổng số hoạt động của từng CLB trên trang đang xem. */
    Map<String, Object> clubs(Page<ClubReportRow> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        return chart("bar", rows.map(ClubReportRow::code).getContent(), List.of(
                dataset(message("report.clubs.members"), rows.map(r -> (Object) r.members()).getContent()),
                dataset(message("report.clubs.activities"), rows.map(r -> (Object) r.activities()).getContent())),
                false);
    }

    private static Map<String, Object> chart(String type, List<String> labels, List<Map<String, Object>> datasets,
                                             boolean percent) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", type);
        chart.put("labels", labels);
        chart.put("datasets", datasets);
        chart.put("percent", percent);
        return chart;
    }

    private static Map<String, Object> dataset(String label, List<?> data) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", label);
        dataset.put("data", data);
        return dataset;
    }

    /** BigDecimal → Double để serializer in ra số; null giữ null (cột trống). */
    private static Object number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }
}
