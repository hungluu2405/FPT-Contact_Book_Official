package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.SessionStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một buổi học trong trang chi tiết lớp học phần; hasAttendance = đã có điểm danh (chỉ đổi được phòng, ghi chú).
 */
public record SessionView(Long id, Short sessionNo, LocalDate date, LocalTime startTime, LocalTime endTime,
                          String roomCode, SessionStatus status, String note, boolean hasAttendance) {

    public boolean cancelled() {
        return status == SessionStatus.CANCELLED;
    }
}
