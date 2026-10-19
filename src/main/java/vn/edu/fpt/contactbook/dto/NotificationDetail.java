package vn.edu.fpt.contactbook.dto;

import org.springframework.data.domain.Page;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết một thông báo. sender = người xem là người gửi; khi đó có thống kê đã đọc (recipientCount, readCount) và
 * một trang người nhận (recipients), ngược lại recipients null.
 */
public record NotificationDetail(Long id, String title, String content, NotificationPriority priority,
                                 NotificationTargetType targetType, String targetLabel, String senderName,
                                 LocalDateTime createdAt, List<FileItem> files, boolean sender, long recipientCount,
                                 long readCount, Page<RecipientRow> recipients) {

    public record FileItem(Long id, String fileName, Long sizeBytes) {
    }

    public record RecipientRow(String fullName, boolean read, LocalDateTime readAt) {
    }

    /** Tỷ lệ đã đọc theo phần trăm, làm tròn số nguyên. */
    public long readPercent() {
        return recipientCount == 0 ? 0 : Math.round(readCount * 100.0 / recipientCount);
    }
}
