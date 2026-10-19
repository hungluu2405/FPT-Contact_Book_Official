package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ChatMessageView;
import vn.edu.fpt.contactbook.dto.ChatView;
import vn.edu.fpt.contactbook.dto.ContactView;
import vn.edu.fpt.contactbook.dto.ConversationListItem;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.Conversation;
import vn.edu.fpt.contactbook.model.ConversationMember;
import vn.edu.fpt.contactbook.model.ConversationMemberId;
import vn.edu.fpt.contactbook.model.Message;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.ConversationType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ConversationMemberRepository;
import vn.edu.fpt.contactbook.repository.ConversationRepository;
import vn.edu.fpt.contactbook.repository.MessageRepository;
import vn.edu.fpt.contactbook.repository.MessagingContactRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tin nhắn 1-1 (SRS FR-MSG-01, 02, 04). Chỉ người có vai trò SV, PH, GV, TBM dùng được; chỉ thành viên mở được hội
 * thoại (khác → 403, kể cả id không tồn tại). Gửi tin mới kiểm tra lại MessagingPolicy mỗi lần; hội thoại cũ vẫn đọc
 * được khi hết quan hệ. Hội thoại DIRECT giữa hai người là duy nhất: tạo khi gửi tin đầu tiên, "tìm hoặc tạo" chạy
 * sau khi khóa dòng users có id nhỏ hơn của cặp, nên hai yêu cầu đồng thời không tạo hai hội thoại. Xóa tin là xóa
 * mềm (is_deleted = 1), chỉ tin của chính mình, ghi audit DELETE_MESSAGE.
 * <p>
 * sent_at và last_read_at là datetime2(0) nên đều được cắt về giây trước khi ghi, để so sánh "chưa đọc" (sent_at >
 * last_read_at) đúng như dữ liệu trong database.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final int MAX_CONTENT = 2000;
    /** Số tin mỗi lần hiện / tải thêm tin cũ. */
    public static final int CHAT_PAGE = 30;
    /** Số tin tối đa một lần làm mới tự động. */
    public static final int NEWER_LIMIT = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final MessagingContactRepository contactRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final MessagingPolicy policy;
    private final AuditService auditService;

    /** Tổng số tin chưa đọc (mục "Tin nhắn" trên menu). */
    public long unreadCount(Long userId) {
        return messageRepository.countUnread(userId);
    }

    /** Một trang hội thoại có tin nhắn, hoạt động gần nhất trước; các truy vấn phụ gom theo id của cả trang. */
    @Transactional(readOnly = true)
    public Page<ConversationListItem> conversations(AppUserPrincipal actor, int page) {
        requireUser(actor);
        Long me = actor.getUserId();
        Page<Object[]> rows = conversationRepository.findPageWithLastMessage(me, CatalogSupport.page(page));
        List<Long> ids = rows.getContent().stream().map(r -> (Long) r[0]).toList();
        if (ids.isEmpty()) {
            return rows.map(r -> null);
        }
        List<Long> lastIds = rows.getContent().stream().map(r -> (Long) r[1]).toList();
        Map<Long, Message> lastById = messageRepository.findAllById(lastIds).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));
        Map<Long, String> otherNames = memberRepository.findOthers(ids, me).stream()
                .collect(Collectors.toMap(m -> m.getId().getConversationId(), m -> m.getUser().getFullName(),
                        (a, b) -> a));
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] r : messageRepository.countUnreadByConversation(me, ids)) {
            unread.put((Long) r[0], ((Number) r[1]).longValue());
        }
        return rows.map(r -> {
            Long id = (Long) r[0];
            Message last = lastById.get((Long) r[1]);
            return new ConversationListItem(id, otherNames.get(id), last.isDeleted() ? null : last.getContent(),
                    last.isDeleted(), Objects.equals(last.getSender().getId(), me), last.getSentAt(),
                    unread.getOrDefault(id, 0L));
        });
    }

    /** Danh bạ "Nhắn tin mới": chỉ người được phép nhắn, tìm theo tên hoặc số điện thoại. */
    @Transactional(readOnly = true)
    public Page<ContactView> contacts(AppUserPrincipal actor, String keyword, int page) {
        requireUser(actor);
        Page<User> users = contactRepository.findContacts(actor.getUserId(),
                MessagingPolicy.isTeacher(actor.getRoles()), actor.hasRole(RoleCodes.STUDENT),
                actor.hasRole(RoleCodes.PARENT), LikePatterns.containsIgnoreCase(keyword),
                CatalogSupport.page(page));
        Map<Long, List<String>> roles = new HashMap<>();
        if (!users.isEmpty()) {
            userRoleRepository.findByUserIdIn(users.getContent().stream().map(User::getId).toList())
                    .forEach(ur -> roles.computeIfAbsent(ur.getUser().getId(), k -> new ArrayList<>())
                            .add(ur.getRole().getCode()));
        }
        return users.map(u -> new ContactView(u.getId(), u.getFullName(),
                roles.getOrDefault(u.getId(), List.of()).stream().sorted().toList()));
    }

    /**
     * Mở hội thoại: 30 tin mới nhất, đánh dấu đã đọc tới bây giờ (conversation_members.last_read_at).
     * Không phải thành viên → 403.
     */
    @Transactional
    public ChatView open(AppUserPrincipal actor, Long conversationId) {
        ConversationMember member = requireMember(actor, conversationId);
        List<Message> newestFirst = messageRepository.findByConversationIdOrderByIdDesc(conversationId,
                PageRequest.of(0, CHAT_PAGE + 1));
        member.setLastReadAt(now());
        return chat(actor, conversationId, newestFirst);
    }

    /** Tin cũ hơn beforeId (mảnh "tải tin cũ hơn"); không đổi trạng thái đã đọc. */
    @Transactional(readOnly = true)
    public ChatView older(AppUserPrincipal actor, Long conversationId, Long beforeId) {
        requireMember(actor, conversationId);
        List<Message> newestFirst = messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId,
                beforeId, PageRequest.of(0, CHAT_PAGE + 1));
        return chat(actor, conversationId, newestFirst);
    }

    /** Tin mới hơn afterId (mảnh làm mới tự động); có tin mới thì đánh dấu đã đọc. */
    @Transactional
    public List<ChatMessageView> newer(AppUserPrincipal actor, Long conversationId, long afterId) {
        ConversationMember member = requireMember(actor, conversationId);
        List<Message> messages = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId,
                afterId, PageRequest.of(0, NEWER_LIMIT));
        if (!messages.isEmpty()) {
            member.setLastReadAt(now());
        }
        return messages.stream().map(m -> view(m, actor.getUserId())).toList();
    }

    /**
     * Khung chat với một người (từ danh bạ): người không được nhắn → 403. Đã có hội thoại thì conversationId khác
     * null (giao diện chuyển tới hội thoại đó); chưa có thì là khung trống, tin đầu tiên sẽ tạo hội thoại.
     */
    @Transactional(readOnly = true)
    public ChatView startWith(AppUserPrincipal actor, Long otherUserId) {
        requireUser(actor);
        if (!policy.canMessage(actor, otherUserId)) {
            throw new AccessDeniedException("Not allowed to message this user");
        }
        List<Long> existing = conversationRepository.findDirectIds(actor.getUserId(), otherUserId);
        String otherName = userRepository.findById(otherUserId).map(User::getFullName).orElseThrow();
        return new ChatView(existing.isEmpty() ? null : existing.getFirst(), otherUserId, otherName, List.of(),
                false, true);
    }

    /** Gửi tin cho một người: tìm hoặc tạo hội thoại DIRECT rồi lưu tin; trả id hội thoại. Không được nhắn → 403. */
    @Transactional
    public Long sendTo(AppUserPrincipal actor, Long otherUserId, String content) {
        requireUser(actor);
        String text = requireContent(content);
        if (!policy.canMessage(actor, otherUserId)) {
            throw new AccessDeniedException("Not allowed to message this user");
        }
        Conversation conversation = findOrCreateDirect(actor.getUserId(), otherUserId);
        save(conversation, actor.getUserId(), text);
        return conversation.getId();
    }

    /** Gửi tin trong hội thoại đã có: thành viên mới gửi được (403); hết quan hệ thì báo lỗi nghiệp vụ. */
    @Transactional
    public void send(AppUserPrincipal actor, Long conversationId, String content) {
        ConversationMember member = requireMember(actor, conversationId);
        String text = requireContent(content);
        Long other = otherMember(conversationId, actor.getUserId());
        if (!policy.canMessage(actor, other)) {
            throw new BusinessException(MessageMessages.CANNOT_SEND);
        }
        save(member.getConversation(), actor.getUserId(), text);
    }

    /**
     * Xóa mềm tin của chính mình. Tin không thuộc hội thoại, của người khác hoặc không tồn tại → 403. Tin đã xóa
     * thì không làm gì.
     */
    @Transactional
    public void delete(AppUserPrincipal actor, Long conversationId, Long messageId, String ipAddress) {
        requireMember(actor, conversationId);
        Message message = messageRepository.findById(messageId)
                .filter(m -> Objects.equals(m.getConversation().getId(), conversationId))
                .filter(m -> Objects.equals(m.getSender().getId(), actor.getUserId()))
                .orElseThrow(() -> new AccessDeniedException("Message not deletable"));
        if (message.isDeleted()) {
            return;
        }
        message.setDeleted(true);
        auditService.log(actor.getUserId(), AuditActions.DELETE_MESSAGE, AuditActions.ENTITY_MESSAGE, messageId,
                "conversation=" + conversationId, ipAddress);
    }

    /**
     * Tìm hoặc tạo hội thoại DIRECT của hai người. Khóa dòng users có id nhỏ hơn trước khi tìm: yêu cầu thứ hai của
     * cùng cặp phải chờ yêu cầu thứ nhất commit, rồi thấy hội thoại vừa tạo.
     */
    Conversation findOrCreateDirect(Long me, Long other) {
        userRepository.findByIdForUpdate(Math.min(me, other))
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        List<Long> existing = conversationRepository.findDirectIds(me, other);
        if (!existing.isEmpty()) {
            return conversationRepository.getReferenceById(existing.getFirst());
        }
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(userRepository.getReferenceById(me));
        conversationRepository.save(conversation);
        memberRepository.saveAll(List.of(member(conversation, me), member(conversation, other)));
        return conversation;
    }

    private ConversationMember member(Conversation conversation, Long userId) {
        ConversationMember member = new ConversationMember();
        member.setId(new ConversationMemberId(conversation.getId(), userId));
        member.setConversation(conversation);
        member.setUser(userRepository.getReferenceById(userId));
        return member;
    }

    private void save(Conversation conversation, Long senderId, String text) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(userRepository.getReferenceById(senderId));
        message.setContent(text);
        message.setSentAt(now());
        messageRepository.save(message);
    }

    /** newestFirst lấy dư một tin để biết còn tin cũ hơn không. */
    private ChatView chat(AppUserPrincipal actor, Long conversationId, List<Message> newestFirst) {
        boolean hasOlder = newestFirst.size() > CHAT_PAGE;
        List<ChatMessageView> messages = new ArrayList<>(newestFirst.stream().limit(CHAT_PAGE)
                .map(m -> view(m, actor.getUserId())).toList());
        Collections.reverse(messages);
        ConversationMember other = memberRepository.findOthers(List.of(conversationId), actor.getUserId()).stream()
                .findFirst().orElseThrow(() -> new AccessDeniedException("Conversation has no other member"));
        Long otherId = other.getUser().getId();
        return new ChatView(conversationId, otherId, other.getUser().getFullName(), messages, hasOlder,
                policy.canMessage(actor, otherId));
    }

    private ChatMessageView view(Message m, Long me) {
        return new ChatMessageView(m.getId(), Objects.equals(m.getSender().getId(), me),
                m.isDeleted() ? null : m.getContent(), m.isDeleted(), m.getSentAt());
    }

    private Long otherMember(Long conversationId, Long me) {
        return memberRepository.findOthers(List.of(conversationId), me).stream()
                .map(m -> m.getUser().getId()).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Conversation has no other member"));
    }

    private ConversationMember requireMember(AppUserPrincipal actor, Long conversationId) {
        requireUser(actor);
        return memberRepository.findById(new ConversationMemberId(conversationId, actor.getUserId()))
                .orElseThrow(() -> new AccessDeniedException("Not a conversation member"));
    }

    private static void requireUser(AppUserPrincipal actor) {
        if (!MessagingPolicy.usesMessaging(actor.getRoles())) {
            throw new AccessDeniedException("Messaging not available for this role");
        }
    }

    private static String requireContent(String content) {
        Map<String, String> errors = new HashMap<>();
        String text = CatalogSupport.requireText(content, MAX_CONTENT, "content", MessageMessages.CONTENT_REQUIRED,
                MessageMessages.CONTENT_SIZE, errors);
        CatalogSupport.throwIfAny(errors);
        return text;
    }

    private static LocalDateTime now() {
        return AppTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
