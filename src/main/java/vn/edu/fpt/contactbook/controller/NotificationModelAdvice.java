package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.MessageService;
import vn.edu.fpt.contactbook.service.MessagingPolicy;
import vn.edu.fpt.contactbook.service.NotificationService;

/**
 * Đưa số thông báo chưa đọc (chuông trên thanh trên, mục menu) và số tin nhắn chưa đọc (mục "Tin nhắn") vào Model
 * của mọi trang, mỗi số một truy vấn đếm; không dùng ${@bean} trong template. Chưa đăng nhập thì không có các thuộc
 * tính này; số tin nhắn chỉ tính cho vai trò dùng tin nhắn (SV, PH, GV, TBM).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;
    private final MessageService messageService;

    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(@AuthenticationPrincipal AppUserPrincipal actor) {
        return actor == null ? null : notificationService.unreadCount(actor.getUserId());
    }

    @ModelAttribute("unreadMessages")
    public Long unreadMessages(@AuthenticationPrincipal AppUserPrincipal actor) {
        return actor == null || !MessagingPolicy.usesMessaging(actor.getRoles()) ? null
                : messageService.unreadCount(actor.getUserId());
    }
}
