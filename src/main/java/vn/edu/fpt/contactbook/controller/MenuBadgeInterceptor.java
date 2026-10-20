package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.MessageService;
import vn.edu.fpt.contactbook.service.MessagingPolicy;
import vn.edu.fpt.contactbook.service.NotificationService;
import vn.edu.fpt.contactbook.service.RequestService;

/**
 * Đưa các số đếm của thanh trên và menu vào Model: unreadNotifications (chuông, mục "Thông báo"), unreadMessages
 * (mục "Tin nhắn", chỉ SV, PH, GV, TBM), pendingTeacherRequests (GV, TBM) và pendingAdminRequests (ADMIN). Mỗi số
 * một truy vấn đếm; không dùng ${@bean} trong template.
 * <p>
 * Chỉ tính khi kết quả là một trang đầy đủ (có view, không phải chuyển hướng, không phải mảnh "::"), để ảnh, tải tệp,
 * xuất Excel, POST chuyển hướng và mảnh làm mới tin nhắn (10 giây/lần) không phải chạy các truy vấn này.
 */
@Component
@RequiredArgsConstructor
public class MenuBadgeInterceptor implements HandlerInterceptor {

    private final NotificationService notificationService;
    private final MessageService messageService;
    private final RequestService requestService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (!isFullPage(modelAndView)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)) {
            return;
        }
        modelAndView.addObject("unreadNotifications", notificationService.unreadCount(actor.getUserId()));
        if (MessagingPolicy.usesMessaging(actor.getRoles())) {
            modelAndView.addObject("unreadMessages", messageService.unreadCount(actor.getUserId()));
        }
        if (actor.hasRole(RoleCodes.TEACHER) || actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT)) {
            modelAndView.addObject("pendingTeacherRequests", requestService.pendingForTeacher(actor));
        }
        if (actor.hasRole(RoleCodes.ADMIN)) {
            modelAndView.addObject("pendingAdminRequests", requestService.pendingForAdmin());
        }
    }

    /** Trang đầy đủ: có tên view, không chuyển hướng / forward, không phải mảnh template ("tên :: mảnh"). */
    static boolean isFullPage(ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getViewName() == null) {
            return false;
        }
        String view = modelAndView.getViewName();
        return !view.startsWith("redirect:") && !view.startsWith("forward:") && !view.contains("::");
    }
}
