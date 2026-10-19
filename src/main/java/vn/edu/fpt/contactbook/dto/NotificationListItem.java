package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.NotificationPriority;

import java.time.LocalDateTime;

/**
 * Một dòng trong hộp thư: read = người xem đã đọc.
 */
public record NotificationListItem(Long id, String title, String senderName, NotificationPriority priority,
                                   LocalDateTime createdAt, boolean read) {
}
