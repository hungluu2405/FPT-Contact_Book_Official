package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.SessionStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một buổi trên thời khóa biểu; roomCode null là chưa xếp phòng.
 */
public record ScheduleItem(LocalDate date, LocalTime startTime, LocalTime endTime, String subjectCode,
                           String subjectName, String sectionCode, String roomCode, String teacherName,
                           SessionStatus status, String note) {

    public boolean cancelled() {
        return status == SessionStatus.CANCELLED;
    }

    public boolean makeup() {
        return status == SessionStatus.MAKEUP;
    }
}
