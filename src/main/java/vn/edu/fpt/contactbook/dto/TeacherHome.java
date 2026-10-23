package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Trang chủ giáo viên (và tổ trưởng): buổi dạy hôm nay, số lớp môn học của học kỳ, thông báo mới. Số đơn chờ duyệt lấy
 * từ MenuBadgeInterceptor (pendingTeacherRequests) để không đếm hai lần. semester null khi chưa có học kỳ nào.
 */
public record TeacherHome(SemesterView semester, List<ScheduleItem> today, long sectionCount,
                          List<NotificationListItem> notifications) {
}
