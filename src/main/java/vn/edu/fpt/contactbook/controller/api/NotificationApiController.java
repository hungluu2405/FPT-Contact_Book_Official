package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.NotificationListItem;
import vn.edu.fpt.contactbook.dto.SentNotificationRow;
import vn.edu.fpt.contactbook.dto.api.NotificationDetailResponse;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.dto.api.SentNotificationDetailResponse;
import vn.edu.fpt.contactbook.dto.api.UnreadCountResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.NotificationService;

/** Hộp thư thông báo của người đang đăng nhập (mọi vai trò). Quyền xem và tải tệp kiểm tra ở NotificationService. */
@Tag(name = "Notifications", description = "Thông báo đã nhận")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    @Operation(summary = "Thông báo đã nhận, mới nhất trước")
    @GetMapping
    public PageResponse<NotificationListItem> inbox(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(notificationService.inbox(actor, page));
    }

    @Operation(summary = "Số thông báo chưa đọc")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AppUserPrincipal actor) {
        return new UnreadCountResponse(notificationService.unreadCount(actor.getUserId()));
    }

    @Operation(summary = "Thông báo mình đã gửi, mới nhất trước, kèm số người nhận và số đã đọc")
    @GetMapping("/sent")
    public PageResponse<SentNotificationRow> sent(@AuthenticationPrincipal AppUserPrincipal actor,
                                                  @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(notificationService.sent(actor, page));
    }

    @Operation(summary = "Thông báo mình đã gửi: thống kê và người nhận (read=true đã đọc, false chưa đọc, trống tất cả)")
    @GetMapping("/sent/{id}")
    public SentNotificationDetailResponse sentDetail(@AuthenticationPrincipal AppUserPrincipal actor,
                                                     @PathVariable Long id,
                                                     @RequestParam(required = false) Boolean read,
                                                     @RequestParam(defaultValue = "0") int page) {
        return SentNotificationDetailResponse.from(notificationService.sentDetail(actor, id, read, page));
    }

    @Operation(summary = "Chi tiết thông báo; lần đầu mở thì đánh dấu đã đọc")
    @GetMapping("/{id}")
    public NotificationDetailResponse open(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id) {
        return NotificationDetailResponse.from(notificationService.open(actor, id, null, 0));
    }

    @Operation(summary = "Đánh dấu mọi thông báo là đã đọc; trả số chưa đọc còn lại")
    @PostMapping("/read-all")
    public UnreadCountResponse readAll(@AuthenticationPrincipal AppUserPrincipal actor) {
        notificationService.markAllRead(actor);
        return new UnreadCountResponse(notificationService.unreadCount(actor.getUserId()));
    }

    @Operation(summary = "Tải tệp đính kèm")
    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                             @PathVariable Long fileId) {
        return ApiDownloads.attachment(notificationService.download(actor, id, fileId));
    }
}
