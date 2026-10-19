package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ChatView;
import vn.edu.fpt.contactbook.dto.ConversationListItem;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Conversation;
import vn.edu.fpt.contactbook.model.ConversationMember;
import vn.edu.fpt.contactbook.model.ConversationMemberId;
import vn.edu.fpt.contactbook.model.Message;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.ConversationType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ConversationMemberRepository;
import vn.edu.fpt.contactbook.repository.ConversationRepository;
import vn.edu.fpt.contactbook.repository.MessageRepository;
import vn.edu.fpt.contactbook.repository.MessagingContactRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tin nhắn: tìm hoặc tạo hội thoại có khóa (không tạo trùng), gửi tin (quy tắc, nội dung), mở hội thoại (thành viên,
 * đánh dấu đã đọc, hết quan hệ vẫn đọc được), xóa mềm chỉ tin của mình, số chưa đọc theo hội thoại.
 * Người dùng: phụ huynh users.id 5, giảng viên users.id 3; hội thoại 100 giữa hai người.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

    static final AppUserPrincipal PARENT = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final long CONVERSATION = 100L;

    @Mock ConversationRepository conversationRepository;
    @Mock ConversationMemberRepository memberRepository;
    @Mock MessageRepository messageRepository;
    @Mock MessagingContactRepository contactRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock MessagingPolicy policy;
    @Mock AuditLogRepository auditLogRepository;

    private MessageService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new MessageService(conversationRepository, memberRepository, messageRepository, contactRepository,
                userRepository, userRoleRepository, policy, new AuditService(auditLogRepository, userRepository));
        conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setType(ConversationType.DIRECT);
        when(userRepository.getReferenceById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0), "Người"));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L, "Trần Thị Giáo")));
        when(userRepository.findByIdForUpdate(anyLong())).thenAnswer(inv -> Optional.of(user(inv.getArgument(0), "")));
        when(conversationRepository.getReferenceById(CONVERSATION)).thenReturn(conversation);
        when(memberRepository.findById(new ConversationMemberId(CONVERSATION, 5L)))
                .thenReturn(Optional.of(member(conversation, 5L, "Lê Văn Phụ")));
        when(memberRepository.findById(new ConversationMemberId(CONVERSATION, 3L)))
                .thenReturn(Optional.of(member(conversation, 3L, "Trần Thị Giáo")));
        when(memberRepository.findOthers(List.of(CONVERSATION), 5L))
                .thenReturn(List.of(member(conversation, 3L, "Trần Thị Giáo")));
        when(memberRepository.findOthers(List.of(CONVERSATION), 3L))
                .thenReturn(List.of(member(conversation, 5L, "Lê Văn Phụ")));
        when(policy.canMessage(PARENT, 3L)).thenReturn(true);
        when(policy.canMessage(TEACHER, 5L)).thenReturn(true);
    }

    @Nested
    @DisplayName("Tìm hoặc tạo hội thoại")
    class FindOrCreate {

        @Test
        @DisplayName("Đã có hội thoại: khóa dòng users id nhỏ hơn TRƯỚC khi tìm, dùng lại, không tạo mới")
        void reusesExisting() {
            when(conversationRepository.findDirectIds(5L, 3L)).thenReturn(List.of(CONVERSATION));

            Long id = service.sendTo(PARENT, 3L, "Chào thầy");

            assertThat(id).isEqualTo(CONVERSATION);
            InOrder order = inOrder(userRepository, conversationRepository);
            order.verify(userRepository).findByIdForUpdate(3L);
            order.verify(conversationRepository).findDirectIds(5L, 3L);
            verify(conversationRepository, never()).save(any());
            verify(memberRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Chưa có: tạo một hội thoại DIRECT với đúng hai thành viên rồi lưu tin")
        void createsWithTwoMembers() {
            when(conversationRepository.findDirectIds(5L, 3L)).thenReturn(List.of());
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
                Conversation c = inv.getArgument(0);
                c.setId(200L);
                return c;
            });

            Long id = service.sendTo(PARENT, 3L, "Chào thầy");

            assertThat(id).isEqualTo(200L);
            ArgumentCaptor<Conversation> created = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(created.capture());
            assertThat(created.getValue().getType()).isEqualTo(ConversationType.DIRECT);
            assertThat(created.getValue().getCreatedBy().getId()).isEqualTo(5L);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<ConversationMember>> members = ArgumentCaptor.forClass(Collection.class);
            verify(memberRepository).saveAll(members.capture());
            assertThat(members.getValue()).extracting(ConversationMember::getId)
                    .containsExactly(new ConversationMemberId(200L, 5L), new ConversationMemberId(200L, 3L));
            ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(message.capture());
            assertThat(message.getValue().getConversation().getId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("Thứ tự khóa không phụ thuộc ai gửi: luôn khóa id nhỏ hơn của cặp")
        void lockIsSymmetric() {
            when(conversationRepository.findDirectIds(anyLong(), anyLong())).thenReturn(List.of(CONVERSATION));

            service.sendTo(PARENT, 3L, "a");
            service.sendTo(TEACHER, 5L, "b");

            verify(userRepository, times(2)).findByIdForUpdate(3L);
            verify(userRepository, never()).findByIdForUpdate(5L);
        }
    }

    @Nested
    @DisplayName("Gửi tin")
    class Send {

        @Test
        @DisplayName("Không được nhắn (GV không liên quan): 403, không khóa, không lưu")
        void notAllowed() {
            when(policy.canMessage(PARENT, 2L)).thenReturn(false);

            assertThatThrownBy(() -> service.sendTo(PARENT, 2L, "Chào thầy"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(userRepository, never()).findByIdForUpdate(anyLong());
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("Nội dung rỗng hoặc chỉ khoảng trắng: lỗi ô content, không lưu")
        void blank() {
            for (String content : new String[]{null, "", "   \n "}) {
                assertThatThrownBy(() -> service.sendTo(PARENT, 3L, content))
                        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                                .containsEntry("content", MessageMessages.CONTENT_REQUIRED));
            }
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("Đúng 2000 ký tự được gửi; 2001 ký tự bị từ chối")
        void lengthBoundary() {
            when(conversationRepository.findDirectIds(5L, 3L)).thenReturn(List.of(CONVERSATION));

            service.sendTo(PARENT, 3L, "a".repeat(2000));
            assertThatThrownBy(() -> service.sendTo(PARENT, 3L, "a".repeat(2001)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("content", MessageMessages.CONTENT_SIZE));
            verify(messageRepository).save(any());
        }

        @Test
        @DisplayName("Tin được lưu đã bỏ khoảng trắng hai đầu, giữ xuống dòng bên trong, sent_at cắt về giây")
        void savedMessage() {
            service.send(PARENT, CONVERSATION, "  Dòng 1\nDòng 2  ");

            ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(message.capture());
            assertThat(message.getValue().getContent()).isEqualTo("Dòng 1\nDòng 2");
            assertThat(message.getValue().getSender().getId()).isEqualTo(5L);
            assertThat(message.getValue().getSentAt().getNano()).isZero();
            assertThat(message.getValue().isDeleted()).isFalse();
        }

        @Test
        @DisplayName("Hội thoại cũ khi hết quan hệ (hết học kỳ): báo lỗi nghiệp vụ, không lưu")
        void relationEnded() {
            when(policy.canMessage(PARENT, 3L)).thenReturn(false);

            assertThatThrownBy(() -> service.send(PARENT, CONVERSATION, "Chào thầy"))
                    .isInstanceOf(BusinessException.class).hasMessage(MessageMessages.CANNOT_SEND);
            verify(messageRepository, never()).save(any());
        }

        @Test
        @DisplayName("Không phải thành viên hoặc hội thoại không tồn tại: 403")
        void notMember() {
            assertThatThrownBy(() -> service.send(PARENT, 999L, "x")).isInstanceOf(AccessDeniedException.class);
            verify(messageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Mở hội thoại")
    class Open {

        @Test
        @DisplayName("Thành viên mở: tin cũ trước mới sau, đánh dấu đã đọc tới bây giờ (cắt về giây)")
        void marksRead() {
            ConversationMember me = member(conversation, 5L, "Lê Văn Phụ");
            when(memberRepository.findById(new ConversationMemberId(CONVERSATION, 5L))).thenReturn(Optional.of(me));
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(CONVERSATION), any()))
                    .thenReturn(List.of(message(12L, 3L, "Mới", false), message(11L, 5L, "Cũ", false)));

            ChatView view = service.open(PARENT, CONVERSATION);

            assertThat(view.messages()).extracting(m -> m.id()).containsExactly(11L, 12L);
            assertThat(view.messages().get(0).mine()).isTrue();
            assertThat(view.messages().get(1).mine()).isFalse();
            assertThat(view.hasOlder()).isFalse();
            assertThat(view.canSend()).isTrue();
            assertThat(view.otherName()).isEqualTo("Trần Thị Giáo");
            assertThat(me.getLastReadAt()).isNotNull();
            assertThat(me.getLastReadAt().getNano()).isZero();
        }

        @Test
        @DisplayName("Có hơn 30 tin: hiện 30 tin mới nhất và báo còn tin cũ")
        void hasOlder() {
            List<Message> newestFirst = new ArrayList<>(LongStream.rangeClosed(1, 31).map(i -> 32 - i)
                    .mapToObj(id -> message(id, 3L, "t" + id, false)).toList());
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(CONVERSATION), any())).thenReturn(newestFirst);

            ChatView view = service.open(PARENT, CONVERSATION);

            assertThat(view.hasOlder()).isTrue();
            assertThat(view.messages()).hasSize(30);
            assertThat(view.oldestId()).isEqualTo(2L);
            assertThat(view.newestId()).isEqualTo(31L);
        }

        @Test
        @DisplayName("Hết quan hệ: vẫn đọc được, canSend = false")
        void readOnlyAfterRelationEnds() {
            when(policy.canMessage(PARENT, 3L)).thenReturn(false);
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(CONVERSATION), any()))
                    .thenReturn(List.of(message(11L, 3L, "Cũ", false)));

            ChatView view = service.open(PARENT, CONVERSATION);

            assertThat(view.messages()).hasSize(1);
            assertThat(view.canSend()).isFalse();
        }

        @Test
        @DisplayName("Tin đã xóa: không trả nội dung")
        void deletedHidden() {
            when(messageRepository.findByConversationIdOrderByIdDesc(eq(CONVERSATION), any()))
                    .thenReturn(List.of(message(11L, 3L, "Bí mật", true)));

            ChatView view = service.open(PARENT, CONVERSATION);

            assertThat(view.messages().getFirst().deleted()).isTrue();
            assertThat(view.messages().getFirst().content()).isNull();
        }

        @Test
        @DisplayName("Người ngoài hoặc chỉ ADMIN: 403")
        void outsiderAndAdmin() {
            AppUserPrincipal outsider = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
            assertThatThrownBy(() -> service.open(outsider, CONVERSATION)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.open(ADMIN, CONVERSATION)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.conversations(ADMIN, 0)).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Làm mới: có tin mới thì đánh dấu đã đọc, không có thì giữ nguyên")
        void newer() {
            ConversationMember me = member(conversation, 5L, "Lê Văn Phụ");
            when(memberRepository.findById(new ConversationMemberId(CONVERSATION, 5L))).thenReturn(Optional.of(me));
            when(messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(eq(CONVERSATION), eq(12L), any()))
                    .thenReturn(List.of());
            assertThat(service.newer(PARENT, CONVERSATION, 12L)).isEmpty();
            assertThat(me.getLastReadAt()).isNull();

            when(messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(eq(CONVERSATION), eq(12L), any()))
                    .thenReturn(List.of(message(13L, 3L, "Mới", false)));
            assertThat(service.newer(PARENT, CONVERSATION, 12L)).extracting(m -> m.id()).containsExactly(13L);
            assertThat(me.getLastReadAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Xóa tin")
    class Delete {

        @Test
        @DisplayName("Tin của mình: is_deleted = 1 (không xóa dòng) và ghi audit DELETE_MESSAGE")
        void ownMessage() {
            Message own = message(11L, 5L, "Nhầm", false);
            when(messageRepository.findById(11L)).thenReturn(Optional.of(own));

            service.delete(PARENT, CONVERSATION, 11L, "127.0.0.1");

            assertThat(own.isDeleted()).isTrue();
            verify(messageRepository, never()).delete(any());
            ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(AuditActions.DELETE_MESSAGE);
            assertThat(audit.getValue().getEntityId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("Tin của người khác: 403, không đổi")
        void othersMessage() {
            Message others = message(12L, 3L, "Của thầy", false);
            when(messageRepository.findById(12L)).thenReturn(Optional.of(others));

            assertThatThrownBy(() -> service.delete(PARENT, CONVERSATION, 12L, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(others.isDeleted()).isFalse();
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tin thuộc hội thoại khác hoặc không tồn tại: 403")
        void wrongConversation() {
            Conversation another = new Conversation();
            another.setId(101L);
            Message elsewhere = message(13L, 5L, "x", false);
            elsewhere.setConversation(another);
            when(messageRepository.findById(13L)).thenReturn(Optional.of(elsewhere));

            assertThatThrownBy(() -> service.delete(PARENT, CONVERSATION, 13L, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.delete(PARENT, CONVERSATION, 999L, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(elsewhere.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("Tin đã xóa: không làm gì, không ghi audit lần nữa")
        void alreadyDeleted() {
            when(messageRepository.findById(11L)).thenReturn(Optional.of(message(11L, 5L, "x", true)));

            service.delete(PARENT, CONVERSATION, 11L, null);

            verify(auditLogRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Danh sách hội thoại và số chưa đọc")
    class ListAndUnread {

        @Test
        @DisplayName("Mỗi dòng có người kia, tin cuối, số chưa đọc; hội thoại không có tin chưa đọc là 0")
        void items() {
            Conversation second = new Conversation();
            second.setId(101L);
            Page<Object[]> rows = new PageImpl<>(List.of(new Object[]{CONVERSATION, 12L}, new Object[]{101L, 20L}),
                    PageRequest.of(0, 20), 2);
            when(conversationRepository.findPageWithLastMessage(eq(5L), any())).thenReturn(rows);
            Message deletedLast = message(20L, 5L, "Đã xóa", true);
            deletedLast.setConversation(second);
            when(messageRepository.findAllById(List.of(12L, 20L)))
                    .thenReturn(List.of(message(12L, 3L, "Chào anh", false), deletedLast));
            when(memberRepository.findOthers(List.of(CONVERSATION, 101L), 5L))
                    .thenReturn(List.of(member(conversation, 3L, "Trần Thị Giáo"), member(second, 2L, "Nguyễn Văn Trưởng")));
            when(messageRepository.countUnreadByConversation(eq(5L), anyCollection()))
                    .thenReturn(List.<Object[]>of(new Object[]{CONVERSATION, 2L}));

            List<ConversationListItem> items = service.conversations(PARENT, 0).getContent();

            assertThat(items).hasSize(2);
            assertThat(items.get(0)).satisfies(i -> {
                assertThat(i.otherName()).isEqualTo("Trần Thị Giáo");
                assertThat(i.lastContent()).isEqualTo("Chào anh");
                assertThat(i.lastMine()).isFalse();
                assertThat(i.unread()).isEqualTo(2L);
            });
            assertThat(items.get(1)).satisfies(i -> {
                assertThat(i.otherName()).isEqualTo("Nguyễn Văn Trưởng");
                assertThat(i.lastDeleted()).isTrue();
                assertThat(i.lastContent()).isNull();
                assertThat(i.lastMine()).isTrue();
                assertThat(i.unread()).isZero();
            });
        }

        @Test
        @DisplayName("Tổng số chưa đọc lấy bằng một truy vấn đếm")
        void unreadCount() {
            when(messageRepository.countUnread(5L)).thenReturn(4L);
            assertThat(service.unreadCount(5L)).isEqualTo(4L);
        }
    }

    private Message message(Long id, Long senderId, String content, boolean deleted) {
        Message message = new Message();
        message.setId(id);
        message.setConversation(conversation);
        message.setSender(user(senderId, "Người " + senderId));
        message.setContent(content);
        message.setDeleted(deleted);
        message.setSentAt(AppTime.now());
        return message;
    }

    static ConversationMember member(Conversation conversation, Long userId, String name) {
        ConversationMember member = new ConversationMember();
        member.setId(new ConversationMemberId(conversation.getId(), userId));
        member.setConversation(conversation);
        member.setUser(user(userId, name));
        return member;
    }

    static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }
}
