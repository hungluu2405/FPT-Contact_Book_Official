package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Trang chủ quản trị: học kỳ hiện tại, số tài khoản đang hoạt động, số học sinh, số lớp môn học của học kỳ hiện tại và
 * thông báo mới. Số đơn chờ xử lý lấy từ MenuBadgeInterceptor (pendingAdminRequests) để không đếm hai lần. semester
 * null khi chưa có học kỳ nào (sectionCount = 0).
 */
public record AdminHome(SemesterView semester, long activeUsers, long students, long sectionCount,
                        List<NotificationListItem> notifications) {
}
