package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.RequestService.View;

/**
 * Xử lý đơn của ADMIN (SRS FR-REQ-05): đơn khác (bảng điểm, xác nhận sinh viên, khác) và đơn nghỉ không chọn lớp của
 * sinh viên không có giảng viên chủ nhiệm. ADMIN mở được mọi đơn nhưng chỉ duyệt phần thuộc mình.
 */
@Controller
@RequestMapping(AdminRequestController.BASE)
@RequiredArgsConstructor
public class AdminRequestController {

    static final String BASE = "/admin/requests";
    private static final String TITLE = "Xử lý đơn";

    private final RequestService requestService;
    private final RequestPages pages;

    @ModelAttribute("activeMenu")
    String activeMenu() {
        return "admin-requests";
    }

    /** status: PENDING (mặc định), APPROVED, REJECTED, CANCELLED hoặc ALL. */
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        return pages.reviewList(model, actor, View.ADMIN, status, page, BASE, TITLE);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        return pages.detail(model, requestService.detail(actor, View.ADMIN, null, id), BASE, TITLE);
    }

    @PostMapping("/{id:\\d+}/review")
    public String review(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(required = false) ReviewDecision decision,
                         @RequestParam(required = false) String comment, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return pages.review(actor, View.ADMIN, id, decision, comment, request, redirectAttributes, BASE);
    }
}
