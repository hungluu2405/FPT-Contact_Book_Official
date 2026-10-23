package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Trang chủ học sinh: buổi học hôm nay, lịch thi sắp tới, các môn đang vượt ngưỡng vắng, thông báo mới.
 * semester null khi chưa có học kỳ nào.
 */
public record StudentHome(SemesterView semester, List<ScheduleItem> today, List<ExamView> upcomingExams,
                          List<StudentAttendanceRow> attendanceWarnings, List<NotificationListItem> notifications) {
}
