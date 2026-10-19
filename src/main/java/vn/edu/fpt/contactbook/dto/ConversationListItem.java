package vn.edu.fpt.contactbook.dto;

import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách hội thoại: người kia, tin cuối (lastContent null khi tin đã bị xóa), lastMine = tin cuối
 * do người xem gửi, unread = số tin chưa đọc của người xem.
 */
public record ConversationListItem(Long id, String otherName, String lastContent, boolean lastDeleted,
                                   boolean lastMine, LocalDateTime lastSentAt, long unread) {
}
