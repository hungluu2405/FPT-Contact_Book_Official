package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.NotificationDetail;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;

import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết thông báo cho người nhận; thống kê người đọc (chỉ người gửi thấy trên web) không có ở đây. */
public record NotificationDetailResponse(Long id, String title, String content, NotificationPriority priority,
                                         String targetLabel, String senderName, LocalDateTime createdAt,
                                         List<NotificationDetail.FileItem> files) {

    public static NotificationDetailResponse from(NotificationDetail detail) {
        return new NotificationDetailResponse(detail.id(), detail.title(), detail.content(), detail.priority(),
                detail.targetLabel(), detail.senderName(), detail.createdAt(), detail.files());
    }
}
