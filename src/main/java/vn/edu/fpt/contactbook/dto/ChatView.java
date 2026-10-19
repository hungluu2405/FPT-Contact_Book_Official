package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Khung chat với một người: conversationId null khi chưa có hội thoại (tin đầu tiên sẽ tạo). messages xếp cũ trước,
 * mới sau; hasOlder = còn tin cũ hơn tin đầu tiên; canSend = còn được gửi tin mới cho người kia.
 */
public record ChatView(Long conversationId, Long otherUserId, String otherName, List<ChatMessageView> messages,
                       boolean hasOlder, boolean canSend) {

    /** Id tin cũ nhất đang hiện (mốc "tải tin cũ hơn"), null khi chưa có tin. */
    public Long oldestId() {
        return messages.isEmpty() ? null : messages.getFirst().id();
    }

    /** Id tin mới nhất đang hiện (mốc làm mới tự động), 0 khi chưa có tin. */
    public long newestId() {
        return messages.isEmpty() ? 0L : messages.getLast().id();
    }
}
