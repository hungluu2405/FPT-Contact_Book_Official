package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttachmentDownload;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.dto.NotificationDetail;
import vn.edu.fpt.contactbook.dto.NotificationForm;
import vn.edu.fpt.contactbook.dto.NotificationFormOptions;
import vn.edu.fpt.contactbook.dto.NotificationListItem;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.SentNotificationRow;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Notification;
import vn.edu.fpt.contactbook.model.NotificationFile;
import vn.edu.fpt.contactbook.model.NotificationRecipient;
import vn.edu.fpt.contactbook.model.NotificationRecipientId;
import vn.edu.fpt.contactbook.model.StoredFile;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.NotificationFileRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.repository.NotificationRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StoredFileRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thông báo (SRS FR-NOTI-01..04). Gửi tay: kiểm tra lại phạm vi ở NotificationAudience (không tin form), xác định
 * người nhận, lưu thông báo + người nhận + tệp đính kèm trong MỘT transaction; người nhận đã loại trùng, loại người
 * gửi, chỉ tài khoản đang hoạt động. Gửi tự động (đổi lịch, công bố điểm, vắng vượt ngưỡng) dùng cùng cách lưu,
 * chạy trong transaction của thao tác gây ra nó. Mỗi lần gửi ghi audit SEND_NOTIFICATION (phạm vi, số người nhận).
 * Xem chi tiết: chỉ người nhận hoặc người gửi; tải tệp: người nhận, người gửi hoặc ADMIN.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationFileRepository notificationFileRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSectionRepository classSectionRepository;
    private final NotificationAudience audience;
    private final AttachmentStorage attachmentStorage;
    private final CatalogLookupService catalogLookupService;
    private final UserAccessPolicy userAccessPolicy;
    private final AuditService auditService;

    /** Lựa chọn trên form gửi; người không được gửi phạm vi nào → 403. */
    @Transactional(readOnly = true)
    public NotificationFormOptions formOptions(AppUserPrincipal actor) {
        Set<NotificationTargetType> types = audience.allowedTypes(actor);
        if (types.isEmpty()) {
            throw new AccessDeniedException("Not allowed to send notifications");
        }
        boolean admin = userAccessPolicy.isAdmin(actor);
        List<OptionView> roles = admin ? roleRepository.findAll().stream()
                .map(r -> new OptionView(r.getId(), r.getCode(), r.getCode(), null)).toList() : List.of();
        List<OptionView> departments = admin ? catalogLookupService.departments()
                : types.contains(NotificationTargetType.DEPARTMENT)
                ? catalogLookupService.departments(audience.headDepartments(actor)) : List.of();
        List<OptionView> classes = admin ? catalogLookupService.classes() : List.of();
        List<OptionView> sections = audience.sections(actor).stream()
                .map(s -> new OptionView(s.getId(), s.getCode(), s.getSubject().getCode() + " - "
                        + s.getSubject().getName() + " (" + s.getSemester().getCode() + ")", null))
                .toList();
        return new NotificationFormOptions(List.copyOf(types), roles, departments, classes, sections);
    }

    /** Gửi tay; trả id thông báo. Lỗi theo ô ném BusinessException; phạm vi ngoài quyền ném 403. */
    @Transactional
    public Long send(AppUserPrincipal actor, NotificationForm form, List<AttachmentUpload> files, String ipAddress) {
        Map<String, String> errors = new LinkedHashMap<>();
        String title = CatalogSupport.requireText(form.getTitle(), 200, "title", NotificationMessages.TITLE_REQUIRED,
                NotificationMessages.TITLE_SIZE, errors);
        String content = CatalogSupport.trimToNull(form.getContent());
        if (content == null) {
            errors.putIfAbsent("content", NotificationMessages.CONTENT_REQUIRED);
        }
        if (form.getPriority() == null) {
            errors.putIfAbsent("priority", NotificationMessages.PRIORITY_REQUIRED);
        }
        NotificationTargetType type = form.getTargetType();
        Long targetId = null;
        if (type == null) {
            errors.putIfAbsent("targetType", NotificationMessages.TARGET_REQUIRED);
        } else {
            targetId = resolveTarget(actor, type, form, errors);
        }
        CatalogSupport.throwIfAny(errors);
        audience.requireCanSend(actor, type, targetId);
        attachmentStorage.validate(files);

        Set<Long> recipients = audience.recipients(type, targetId);
        recipients.remove(actor.getUserId());
        if (recipients.isEmpty()) {
            throw new BusinessException(NotificationMessages.NO_RECIPIENTS);
        }
        Notification notification = save(actor.getUserId(), title, content, form.getPriority(), type, targetId,
                recipients, ipAddress, false);
        User uploader = userRepository.getReferenceById(actor.getUserId());
        for (AttachmentUpload upload : files) {
            AttachmentStorage.Stored stored = attachmentStorage.store(upload);
            StoredFile file = new StoredFile();
            file.setFileName(stored.fileName());
            file.setFileUrl(stored.key());
            file.setContentType(stored.contentType());
            file.setSizeBytes(stored.sizeBytes());
            file.setUploadedBy(uploader);
            storedFileRepository.save(file);
            NotificationFile link = new NotificationFile();
            link.setNotification(notification);
            link.setFile(file);
            notificationFileRepository.save(link);
        }
        return notification.getId();
    }

    /**
     * Gửi tự động trong transaction của thao tác gây ra nó; người gửi là người thực hiện thao tác. Không kiểm tra
     * phạm vi (hệ thống tự chọn người nhận). Không còn người nhận nào thì không gửi.
     */
    @Transactional
    public Optional<Long> sendAutomatic(Long senderUserId, String title, String content,
                                        NotificationPriority priority, NotificationTargetType type, Long targetId,
                                        Collection<Long> recipientUserIds) {
        Set<Long> recipients = new LinkedHashSet<>(recipientUserIds);
        recipients.remove(senderUserId);
        if (recipients.isEmpty()) {
            return Optional.empty();
        }
        String safeTitle = title.length() > 200 ? title.substring(0, 200) : title;
        return Optional.of(save(senderUserId, safeTitle, content, priority, type, targetId, recipients, null, true)
                .getId());
    }

    @Transactional(readOnly = true)
    public Page<NotificationListItem> inbox(AppUserPrincipal actor, int page) {
        return recipientRepository.findInbox(actor.getUserId(), CatalogSupport.page(page))
                .map(nr -> {
                    Notification n = nr.getNotification();
                    return new NotificationListItem(n.getId(), n.getTitle(), n.getSender().getFullName(),
                            n.getPriority(), n.getCreatedAt(), nr.isRead());
                });
    }

    public long unreadCount(Long userId) {
        return recipientRepository.countUnread(userId);
    }

    @Transactional
    public int markAllRead(AppUserPrincipal actor) {
        return recipientRepository.markAllRead(actor.getUserId(), AppTime.now());
    }

    /**
     * Chi tiết; người nhận mở lần đầu thì đánh dấu đã đọc. Người gửi thấy thêm thống kê và một trang người nhận
     * (read: true đã đọc, false chưa đọc, null tất cả). Người khác → 403 (kể cả id không tồn tại, để không dò id).
     */
    @Transactional
    public NotificationDetail open(AppUserPrincipal actor, Long notificationId, Boolean read, int page) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AccessDeniedException("Notification not visible"));
        boolean sender = Objects.equals(notification.getSender().getId(), actor.getUserId());
        Optional<NotificationRecipient> recipient = recipientRepository.findById(
                new NotificationRecipientId(notificationId, actor.getUserId()));
        if (!sender && recipient.isEmpty()) {
            throw new AccessDeniedException("Notification not visible");
        }
        recipient.filter(r -> !r.isRead()).ifPresent(r -> {
            r.setRead(true);
            r.setReadAt(AppTime.now());
        });
        List<NotificationDetail.FileItem> files = notificationFileRepository
                .findByIdNotificationIdOrderByIdFileIdAsc(notificationId).stream()
                .map(nf -> new NotificationDetail.FileItem(nf.getFile().getId(), nf.getFile().getFileName(),
                        nf.getFile().getSizeBytes()))
                .toList();
        long total = 0;
        long readCount = 0;
        Page<NotificationDetail.RecipientRow> recipients = null;
        if (sender) {
            List<Object[]> stats = recipientRepository.countReadStats(List.of(notificationId));
            if (!stats.isEmpty()) {
                total = ((Number) stats.getFirst()[1]).longValue();
                readCount = ((Number) stats.getFirst()[2]).longValue();
            }
            recipients = recipientRepository.findRecipients(notificationId, read, CatalogSupport.page(page))
                    .map(r -> new NotificationDetail.RecipientRow(r.getUser().getFullName(), r.isRead(),
                            r.getReadAt()));
        }
        return new NotificationDetail(notification.getId(), notification.getTitle(), notification.getContent(),
                notification.getPriority(), notification.getTargetType(),
                targetLabel(notification.getTargetType(), notification.getTargetId()),
                notification.getSender().getFullName(), notification.getCreatedAt(), files, sender, total, readCount,
                recipients);
    }

    /** Thông báo đã gửi kèm thống kê đã đọc (một truy vấn gộp cho cả trang). */
    @Transactional(readOnly = true)
    public Page<SentNotificationRow> sent(AppUserPrincipal actor, int page) {
        Page<Notification> notifications = notificationRepository.findBySenderIdOrderByCreatedAtDescIdDesc(
                actor.getUserId(), CatalogSupport.page(page));
        List<Long> ids = notifications.map(Notification::getId).getContent();
        Map<Long, long[]> stats = ids.isEmpty() ? Map.of() : recipientRepository.countReadStats(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0],
                        row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}));
        return notifications.map(n -> {
            long[] s = stats.getOrDefault(n.getId(), new long[]{0, 0});
            return new SentNotificationRow(n.getId(), n.getTitle(), n.getPriority(), n.getTargetType(),
                    n.getCreatedAt(), s[0], s[1]);
        });
    }

    /** Tệp đính kèm: tệp phải thuộc thông báo; chỉ người nhận, người gửi hoặc ADMIN, người khác → 403. */
    @Transactional(readOnly = true)
    public AttachmentDownload download(AppUserPrincipal actor, Long notificationId, Long fileId) {
        NotificationFile link = notificationFileRepository.findByIdNotificationIdAndIdFileId(notificationId, fileId)
                .orElseThrow(() -> new AccessDeniedException("Attachment not visible"));
        boolean allowed = userAccessPolicy.isAdmin(actor)
                || Objects.equals(link.getNotification().getSender().getId(), actor.getUserId())
                || recipientRepository.existsById(new NotificationRecipientId(notificationId, actor.getUserId()));
        if (!allowed) {
            throw new AccessDeniedException("Attachment not visible");
        }
        StoredFile file = link.getFile();
        return attachmentStorage.resolve(file.getFileUrl())
                .map(path -> new AttachmentDownload(file.getFileName(), file.getContentType(), path))
                .orElseThrow(() -> new NotFoundException(NotificationMessages.FILE_NOT_FOUND));
    }

    /** targetId theo phạm vi: ALL không cần; USER từ số điện thoại; còn lại từ danh sách chọn và phải tồn tại. */
    private Long resolveTarget(AppUserPrincipal actor, NotificationTargetType type, NotificationForm form,
                               Map<String, String> errors) {
        if (type == NotificationTargetType.ALL) {
            return null;
        }
        if (type == NotificationTargetType.USER) {
            String raw = CatalogSupport.trimToNull(form.getRecipientPhone());
            if (raw == null) {
                errors.putIfAbsent("recipientPhone", NotificationMessages.PHONE_REQUIRED);
                return null;
            }
            Optional<User> user = PhoneUtils.normalize(raw).flatMap(userRepository::findByPhone)
                    .filter(User::isActive);
            if (user.isEmpty()) {
                errors.putIfAbsent("recipientPhone", NotificationMessages.PHONE_NOT_FOUND);
                return null;
            }
            return user.get().getId();
        }
        Long id = form.targetId();
        if (id == null) {
            errors.putIfAbsent(form.targetField(), NotificationMessages.TARGET_ID_REQUIRED);
            return null;
        }
        // Người không phải ADMIN chọn đích không tồn tại sẽ bị chặn 403 ở requireCanSend (không dò được id).
        if (userAccessPolicy.isAdmin(actor) && !targetExists(type, id)) {
            errors.putIfAbsent(form.targetField(), NotificationMessages.TARGET_NOT_FOUND);
        }
        return id;
    }

    private boolean targetExists(NotificationTargetType type, Long id) {
        return switch (type) {
            case ROLE -> roleRepository.existsById(id);
            case DEPARTMENT -> departmentRepository.existsById(id);
            case CLASS -> schoolClassRepository.existsById(id);
            case CLASS_SECTION -> classSectionRepository.existsById(id);
            case ALL, USER -> true;
        };
    }

    /** Mô tả phạm vi để hiển thị (mã vai trò, bộ môn, lớp, lớp học phần, họ tên); null nếu không tìm thấy. */
    private String targetLabel(NotificationTargetType type, Long id) {
        if (id == null) {
            return null;
        }
        return switch (type) {
            case ROLE -> roleRepository.findById(id).map(r -> r.getCode()).orElse(null);
            case DEPARTMENT -> departmentRepository.findById(id).map(d -> d.getCode() + " - " + d.getName())
                    .orElse(null);
            case CLASS -> schoolClassRepository.findById(id).map(c -> c.getCode()).orElse(null);
            case CLASS_SECTION -> classSectionRepository.findById(id).map(s -> s.getCode()).orElse(null);
            case USER -> userRepository.findById(id).map(User::getFullName).orElse(null);
            case ALL -> null;
        };
    }

    private Notification save(Long senderUserId, String title, String content, NotificationPriority priority,
                              NotificationTargetType type, Long targetId, Set<Long> recipients, String ipAddress,
                              boolean automatic) {
        Notification notification = new Notification();
        notification.setSender(userRepository.getReferenceById(senderUserId));
        notification.setTitle(title);
        notification.setContent(content);
        notification.setPriority(priority);
        notification.setTargetType(type);
        notification.setTargetId(targetId);
        notificationRepository.save(notification);
        List<NotificationRecipient> rows = new ArrayList<>(recipients.size());
        for (Long userId : recipients) {
            NotificationRecipient row = new NotificationRecipient();
            row.setId(new NotificationRecipientId(notification.getId(), userId));
            row.setNotification(notification);
            row.setUser(userRepository.getReferenceById(userId));
            rows.add(row);
        }
        recipientRepository.saveAll(rows);
        auditService.log(senderUserId, AuditActions.SEND_NOTIFICATION, AuditActions.ENTITY_NOTIFICATION,
                notification.getId(), "target=" + type + (targetId == null ? "" : ":" + targetId) + ", recipients="
                        + recipients.size() + (automatic ? ", automatic=true" : ""), ipAddress);
        return notification;
    }
}
