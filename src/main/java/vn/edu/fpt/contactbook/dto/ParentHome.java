package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Trang chủ phụ huynh: học kỳ hiện tại (null khi chưa có), một thẻ cho mỗi con đã liên kết và thông báo mới.
 */
public record ParentHome(SemesterView semester, List<Child> children, List<NotificationListItem> notifications) {

    /** Tóm tắt của một con: studentId là students.id; danh sách rỗng khi chưa có học kỳ hoặc chưa có dữ liệu. */
    public record Child(Long studentId, String studentCode, String fullName, List<ScheduleItem> today,
                        List<ExamView> upcomingExams, List<StudentAttendanceRow> attendanceWarnings) {
    }
}
