package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Trang ghi nhận tham dự một hoạt động: những người đang giữ chỗ (REGISTERED hoặc ATTENDED), attended = đã tham dự.
 */
public record ClubAttendanceSheet(ActivityView activity, String clubName, List<Row> rows) {

    public record Row(Long userId, String fullName, boolean attended) {
    }
}
