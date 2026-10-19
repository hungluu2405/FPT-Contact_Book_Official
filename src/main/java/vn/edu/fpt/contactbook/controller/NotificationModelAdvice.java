package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.NotificationService;

/**
 * Đưa số thông báo chưa đọc vào Model của mọi trang (chuông trên thanh trên, mục menu) bằng một truy vấn đếm; không
 * dùng ${@bean} trong template. Chưa đăng nhập thì không có thuộc tính này.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(@AuthenticationPrincipal AppUserPrincipal actor) {
        return actor == null ? null : notificationService.unreadCount(actor.getUserId());
    }
}
