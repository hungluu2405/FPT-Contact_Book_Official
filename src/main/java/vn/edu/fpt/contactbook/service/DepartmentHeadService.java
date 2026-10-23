package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.dto.HeadDepartmentView;
import vn.edu.fpt.contactbook.dto.HeadNotificationForm;
import vn.edu.fpt.contactbook.dto.NotificationForm;
import vn.edu.fpt.contactbook.dto.UserDetailView;
import vn.edu.fpt.contactbook.dto.UserFilter;
import vn.edu.fpt.contactbook.dto.UserSummaryView;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;
import java.util.Set;

/**
 * Chức năng của tổ trưởng chuyên môn trên app (giai đoạn 10e): tổ mình phụ trách, giáo viên trong tổ, thông báo nội
 * bộ cho cả tổ hoặc một giáo viên. Phạm vi tổ kiểm tra lại ở UserService và NotificationAudience như trên web.
 */
@Service
@RequiredArgsConstructor
public class DepartmentHeadService {

    private final UserAccessPolicy accessPolicy;
    private final CatalogLookupService catalogLookupService;
    private final UserService userService;
    private final NotificationService notificationService;

    /** Các tổ người xem làm tổ trưởng, theo mã tổ. */
    @Transactional(readOnly = true)
    public List<HeadDepartmentView> departments(AppUserPrincipal actor) {
        Set<Long> ids = requireHead(actor);
        return catalogLookupService.departments(ids).stream()
                .map(d -> new HeadDepartmentView(d.id(), d.code(), d.name(),
                        teachers(actor, d.id(), null, 0).getTotalElements()))
                .toList();
    }

    /** Giáo viên các tổ mình phụ trách (departmentId null: mọi tổ), tìm theo tên, mã, số điện thoại. */
    @Transactional(readOnly = true)
    public Page<UserSummaryView> teachers(AppUserPrincipal actor, Long departmentId, String keyword, int page) {
        requireHead(actor);
        UserFilter filter = new UserFilter();
        filter.setDepartmentId(departmentId);
        filter.setKeyword(keyword);
        return userService.list(actor, filter, page);
    }

    /** Chi tiết một giáo viên trong tổ; người ngoài tổ (kể cả id không tồn tại) → 403. */
    @Transactional(readOnly = true)
    public UserDetailView teacher(AppUserPrincipal actor, Long userId) {
        requireHead(actor);
        return userService.detail(actor, userId);
    }

    /** Gửi thông báo nội bộ; trả id thông báo. Lỗi theo ô ném BusinessException, ngoài phạm vi tổ → 403. */
    @Transactional
    public Long send(AppUserPrincipal actor, HeadNotificationForm head, List<AttachmentUpload> files,
                     String ipAddress) {
        requireHead(actor);
        NotificationForm form = new NotificationForm();
        form.setTitle(head.getTitle());
        form.setContent(head.getContent());
        form.setPriority(head.getPriority());
        if (head.getTeacherUserId() != null) {
            form.setTargetType(NotificationTargetType.USER);
            form.setRecipientPhone(teacher(actor, head.getTeacherUserId()).phone());
        } else {
            form.setTargetType(NotificationTargetType.DEPARTMENT);
            form.setDepartmentId(head.getDepartmentId());
        }
        return notificationService.send(actor, form, files, ipAddress);
    }

    private Set<Long> requireHead(AppUserPrincipal actor) {
        Set<Long> ids = accessPolicy.headDepartmentIds(actor);
        if (ids.isEmpty()) {
            throw new AccessDeniedException("Not a head of department");
        }
        return ids;
    }
}
