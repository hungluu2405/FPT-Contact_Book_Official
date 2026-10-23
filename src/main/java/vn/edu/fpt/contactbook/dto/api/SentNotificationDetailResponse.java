package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.NotificationDetail;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

import java.time.LocalDateTime;
import java.util.List;

/** Thông báo mình đã gửi: nội dung, phạm vi, thống kê đã đọc và một trang người nhận. */
public record SentNotificationDetailResponse(Long id, String title, String content, NotificationPriority priority,
                                             NotificationTargetType targetType, String targetLabel,
                                             LocalDateTime createdAt, List<NotificationDetail.FileItem> files,
                                             long recipientCount, long readCount,
                                             PageResponse<NotificationDetail.RecipientRow> recipients) {

    public static SentNotificationDetailResponse from(NotificationDetail detail) {
        return new SentNotificationDetailResponse(detail.id(), detail.title(), detail.content(), detail.priority(),
                detail.targetType(), detail.targetLabel(), detail.createdAt(), detail.files(),
                detail.recipientCount(), detail.readCount(), PageResponse.from(detail.recipients()));
    }
}
