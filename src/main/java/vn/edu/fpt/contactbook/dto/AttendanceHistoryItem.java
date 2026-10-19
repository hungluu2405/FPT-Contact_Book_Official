package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một buổi trong lịch sử điểm danh theo môn; status null là buổi chưa được điểm danh.
 */
public record AttendanceHistoryItem(Short sessionNo, LocalDate date, LocalTime startTime, LocalTime endTime,
                                    String roomCode, SessionStatus sessionStatus, AttendanceStatus status,
                                    String note) {

    public boolean cancelled() {
        return sessionStatus == SessionStatus.CANCELLED;
    }
}
