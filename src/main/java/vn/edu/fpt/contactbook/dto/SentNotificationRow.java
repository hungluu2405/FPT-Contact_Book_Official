package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

import java.time.LocalDateTime;

/**
 * Một thông báo đã gửi kèm thống kê đã đọc.
 */
public record SentNotificationRow(Long id, String title, NotificationPriority priority,
                                  NotificationTargetType targetType, LocalDateTime createdAt, long recipientCount,
                                  long readCount) {

    public long readPercent() {
        return recipientCount == 0 ? 0 : Math.round(readCount * 100.0 / recipientCount);
    }
}
