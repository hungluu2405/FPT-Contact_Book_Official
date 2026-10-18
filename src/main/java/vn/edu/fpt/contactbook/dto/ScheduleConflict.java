package vn.edu.fpt.contactbook.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một xung đột của buổi học hoặc lịch thi định tạo: ngày giờ định tạo, loại xung đột và mô tả cái bị trùng
 * (tiếng Việt, hiện trong bảng xung đột).
 */
public record ScheduleConflict(LocalDate date, LocalTime startTime, LocalTime endTime, Type type, String detail) {

    public enum Type {
        /** Cùng phòng với buổi học hoặc lịch thi khác. */
        ROOM,
        /** Giảng viên đang dạy lớp khác. */
        TEACHER,
        /** Sinh viên đang ENROLLED có lịch ở lớp khác. */
        STUDENT,
        /** Chồng giờ với buổi khác của chính lớp này. */
        SAME_SECTION,
        /** Trùng đúng ngày và giờ bắt đầu với buổi đã hủy của lớp (uq_sess_cs_slot). */
        CANCELLED_SLOT
    }
}
