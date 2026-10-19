package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.LeaveRequestForm;
import vn.edu.fpt.contactbook.dto.OtherRequestForm;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.RequestService.View;

import java.io.IOException;
import java.util.List;

/**
 * Đơn từ của sinh viên (SRS FR-REQ-01, 03, 04, 05): xem đơn của mình (kể cả đơn phụ huynh gửi thay), gửi đơn nghỉ và
 * đơn khác, hủy đơn mình gửi khi còn chờ duyệt. Phạm vi kiểm tra ở RequestService.
 */
@Controller
@RequestMapping(StudentRequestController.BASE)
@RequiredArgsConstructor
public class StudentRequestController {

    static final String BASE = "/student/requests";
    private static final String TITLE = "Đơn từ";

    private final RequestService requestService;
    private final RequestPages pages;

    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @ModelAttribute("activeMenu")
    String activeMenu() {
        return "student-requests";
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        return pages.list(model, requestService.studentRequests(actor, View.STUDENT, null, page), BASE, TITLE, true);
    }

    @GetMapping("/new")
    public String leaveForm(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("form", RequestPages.newLeaveForm());
        return pages.leaveForm(model, actor, View.STUDENT, null, BASE);
    }

    @PostMapping("/new")
    public String createLeave(@AuthenticationPrincipal AppUserPrincipal actor,
                              @Valid @ModelAttribute("form") LeaveRequestForm form, BindingResult bindingResult,
                              @RequestParam(value = "files", required = false) List<MultipartFile> files, Model model,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        return pages.submitLeave(model, actor, View.STUDENT, null, form, bindingResult, files, request,
                redirectAttributes, BASE);
    }

    @GetMapping("/new-other")
    public String otherForm(Model model) {
        model.addAttribute("form", new OtherRequestForm());
        return pages.otherForm(model, BASE);
    }

    @PostMapping("/new-other")
    public String createOther(@AuthenticationPrincipal AppUserPrincipal actor,
                              @Valid @ModelAttribute("form") OtherRequestForm form, BindingResult bindingResult,
                              @RequestParam(value = "files", required = false) List<MultipartFile> files, Model model,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        return pages.submitOther(model, actor, form, bindingResult, files, request, redirectAttributes, BASE);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        return pages.detail(model, requestService.detail(actor, View.STUDENT, null, id), BASE, TITLE);
    }

    @PostMapping("/{id:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return pages.cancel(actor, id, request, redirectAttributes, BASE);
    }
}
