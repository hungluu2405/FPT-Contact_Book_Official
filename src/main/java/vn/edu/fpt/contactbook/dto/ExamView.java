package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ExamType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một lịch thi; sectionCode, subjectCode, subjectName dùng ở trang lịch thi của giảng viên, sinh viên, phụ huynh.
 */
public record ExamView(Long id, String sectionCode, String subjectCode, String subjectName, ExamType examType,
                       LocalDate date, LocalTime startTime, LocalTime endTime, String roomCode, String note) {
}
