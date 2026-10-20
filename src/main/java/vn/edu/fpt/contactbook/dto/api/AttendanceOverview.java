package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;

import java.util.List;

/** Chuyên cần theo môn của một học kỳ; thresholdPercent là ngưỡng vắng bị cảnh báo (app.attendance.*). */
public record AttendanceOverview(SemesterView semester, int thresholdPercent, List<StudentAttendanceRow> sections) {
}
