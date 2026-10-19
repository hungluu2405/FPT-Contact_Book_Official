package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;

/**
 * Đưa số đơn chờ duyệt vào Model cho mục "Duyệt đơn" trên menu: pendingTeacherRequests với giảng viên / trưởng bộ
 * môn, pendingAdminRequests với ADMIN. Chỉ chạy truy vấn đếm cho vai trò cần; vai trò khác không có thuộc tính này.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class RequestModelAdvice {

    private final RequestService requestService;

    @ModelAttribute
    public void pendingRequests(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        if (actor == null) {
            return;
        }
        if (actor.hasRole(RoleCodes.TEACHER) || actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT)) {
            model.addAttribute("pendingTeacherRequests", requestService.pendingForTeacher(actor));
        }
        if (actor.hasRole(RoleCodes.ADMIN)) {
            model.addAttribute("pendingAdminRequests", requestService.pendingForAdmin());
        }
    }
}
