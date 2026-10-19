package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ConversationMember;
import vn.edu.fpt.contactbook.model.ConversationMemberId;

import java.util.Collection;
import java.util.List;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    /** Các thành viên khác của nhiều hội thoại một lần (kèm tài khoản), cho danh sách hội thoại và khung chat. */
    @Query("""
            SELECT m FROM ConversationMember m JOIN FETCH m.user
            WHERE m.id.conversationId IN :conversationIds AND m.id.userId <> :userId
            """)
    List<ConversationMember> findOthers(@Param("conversationIds") Collection<Long> conversationIds,
                                        @Param("userId") Long userId);
}
