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
 * Duyệt đơn nghỉ của giảng viên (SRS FR-REQ-02): đơn có lớp học phần mình dạy, hoặc đơn không chọn lớp của sinh viên
 * lớp mình chủ nhiệm. Mở hoặc duyệt đơn không phải của mình → 403 (RequestService).
 */
@Controller
@RequestMapping(TeacherRequestController.BASE)
@RequiredArgsConstructor
public class TeacherRequestController {

    static final String BASE = "/teacher/requests";
    private static final String TITLE = "Duyệt đơn";

    private final RequestService requestService;
    private final RequestPages pages;

    @ModelAttribute("activeMenu")
    String activeMenu() {
        return "teacher-requests";
    }

    /** status: PENDING (mặc định), APPROVED, REJECTED, CANCELLED hoặc ALL. */
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        return pages.reviewList(model, actor, View.TEACHER, status, page, BASE, TITLE);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        return pages.detail(model, requestService.detail(actor, View.TEACHER, null, id), BASE, TITLE);
    }

    @PostMapping("/{id:\\d+}/review")
    public String review(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(required = false) ReviewDecision decision,
                         @RequestParam(required = false) String comment, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return pages.review(actor, View.TEACHER, id, decision, comment, request, redirectAttributes, BASE);
    }
}
