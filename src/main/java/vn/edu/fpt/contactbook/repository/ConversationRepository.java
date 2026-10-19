package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Conversation;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Hội thoại DIRECT có cả hai người là thành viên (cũ nhất trước; bình thường chỉ có một). */
    @Query("""
            SELECT c.id FROM Conversation c
            WHERE c.type = vn.edu.fpt.contactbook.model.enums.ConversationType.DIRECT
              AND EXISTS (SELECT 1 FROM ConversationMember m WHERE m.conversation = c AND m.user.id = :userA)
              AND EXISTS (SELECT 1 FROM ConversationMember m WHERE m.conversation = c AND m.user.id = :userB)
            ORDER BY c.id
            """)
    List<Long> findDirectIds(@Param("userA") Long userA, @Param("userB") Long userB);

    /**
     * Một trang hội thoại có tin nhắn của người dùng, hoạt động gần nhất trước: [conversationId, id tin cuối].
     * Sắp theo id tin cuối (IDENTITY tăng theo thời điểm gửi) để không hòa nhau khi sent_at trùng giây.
     */
    @Query(value = """
            SELECT m.id.conversationId, MAX(x.id) FROM ConversationMember m, Message x
            WHERE x.conversation.id = m.id.conversationId AND m.id.userId = :userId
            GROUP BY m.id.conversationId
            ORDER BY MAX(x.id) DESC
            """,
            countQuery = """
            SELECT COUNT(m) FROM ConversationMember m
            WHERE m.id.userId = :userId
              AND EXISTS (SELECT 1 FROM Message x WHERE x.conversation.id = m.id.conversationId)
            """)
    Page<Object[]> findPageWithLastMessage(@Param("userId") Long userId, Pageable pageable);
}
