package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubStatus;

/**
 * Một CLB trong báo cáo CLB (SRS FR-REPORT-03): số thành viên ACTIVE và số hoạt động theo trạng thái hiển thị
 * (PLANNED, ONGOING, DONE tính theo thời gian như trang CLB; CANCELLED là đã hủy).
 */
public record ClubReportRow(Long id, String code, String name, ClubStatus status, long members, long planned,
                            long ongoing, long done, long cancelled) {

    public long activities() {
        return planned + ongoing + done + cancelled;
    }
}
