package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Message;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Điều kiện tin chưa đọc của người dùng :userId (thành viên m): người khác gửi, chưa bị xóa, gửi sau
     * last_read_at (NULL là chưa đọc lần nào).
     */
    String UNREAD = """
            m.id.conversationId = x.conversation.id AND m.id.userId = :userId
              AND x.sender.id <> :userId AND x.isDeleted = false
              AND (m.lastReadAt IS NULL OR x.sentAt > m.lastReadAt)
            """;

    /** Tổng số tin chưa đọc của một người (mục "Tin nhắn" trên menu). */
    @Query("SELECT COUNT(x) FROM Message x, ConversationMember m WHERE " + UNREAD)
    long countUnread(@Param("userId") Long userId);

    /** Số tin chưa đọc theo hội thoại: [conversationId, số tin]; hội thoại không có tin chưa đọc thì không có dòng. */
    @Query("SELECT x.conversation.id, COUNT(x) FROM Message x, ConversationMember m WHERE " + UNREAD
            + " AND x.conversation.id IN :conversationIds GROUP BY x.conversation.id")
    List<Object[]> countUnreadByConversation(@Param("userId") Long userId,
                                             @Param("conversationIds") Collection<Long> conversationIds);

    /** Tin mới nhất trước; dùng Pageable để giới hạn số tin. */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    /** Tin cũ hơn beforeId, mới nhất trước. */
    List<Message> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId,
                                                                 Pageable pageable);

    /** Tin mới hơn afterId, cũ nhất trước. */
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId,
                                                                   Pageable pageable);
}
