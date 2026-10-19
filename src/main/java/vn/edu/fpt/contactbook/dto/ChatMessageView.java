package vn.edu.fpt.contactbook.dto;

import java.time.LocalDateTime;

/**
 * Một tin trong khung chat; content null khi tin đã bị xóa (giao diện hiện "Tin nhắn đã bị xóa").
 */
public record ChatMessageView(Long id, boolean mine, String content, boolean deleted, LocalDateTime sentAt) {
}
