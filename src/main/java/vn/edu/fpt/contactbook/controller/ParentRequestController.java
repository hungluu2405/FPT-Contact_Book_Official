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
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.RequestService.View;
import vn.edu.fpt.contactbook.service.ScheduleViewService;

import java.io.IOException;
import java.util.List;

/**
 * Đơn của con (studentId là students.id): xem mọi đơn của con đã liên kết, gửi đơn nghỉ thay con, hủy đơn mình gửi
 * khi còn chờ duyệt. Con chưa liên kết → 403 (RequestService). /parent/requests mở con đầu tiên đã liên kết.
 */
@Controller
@RequestMapping("/parent")
@RequiredArgsConstructor
public class ParentRequestController {

    private static final String TITLE = "Đơn của con";

    private final RequestService requestService;
    private final ScheduleViewService scheduleViewService;
    private final RequestPages pages;

    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @ModelAttribute("activeMenu")
    String activeMenu() {
        return "parent-requests";
    }

    @GetMapping("/requests")
    public String firstChild(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        List<OptionView> children = scheduleViewService.children(actor);
        if (children.isEmpty()) {
            model.addAttribute("noChildren", true);
            model.addAttribute("pageTitle", TITLE);
            return "requests/list";
        }
        return "redirect:" + base(children.getFirst().id());
    }

    @GetMapping("/children/{studentId:\\d+}/requests")
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        String view = pages.list(model, requestService.studentRequests(actor, View.PARENT, studentId, page),
                base(studentId), TITLE, false);
        model.addAttribute("children", scheduleViewService.children(actor));
        model.addAttribute("studentId", studentId);
        model.addAttribute("childUrlTemplate", "/parent/children/{id}/requests");
        return view;
    }

    @GetMapping("/children/{studentId:\\d+}/requests/new")
    public String leaveForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                            Model model) {
        model.addAttribute("form", RequestPages.newLeaveForm());
        return pages.leaveForm(model, actor, View.PARENT, studentId, base(studentId));
    }

    @PostMapping("/children/{studentId:\\d+}/requests/new")
    public String createLeave(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                              @Valid @ModelAttribute("form") LeaveRequestForm form, BindingResult bindingResult,
                              @RequestParam(value = "files", required = false) List<MultipartFile> files, Model model,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        return pages.submitLeave(model, actor, View.PARENT, studentId, form, bindingResult, files, request,
                redirectAttributes, base(studentId));
    }

    @GetMapping("/children/{studentId:\\d+}/requests/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                         @PathVariable Long id, Model model) {
        return pages.detail(model, requestService.detail(actor, View.PARENT, studentId, id), base(studentId), TITLE);
    }

    @PostMapping("/children/{studentId:\\d+}/requests/{id:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                         @PathVariable Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return pages.cancel(actor, id, request, redirectAttributes, base(studentId));
    }

    private static String base(Long studentId) {
        return "/parent/children/" + studentId + "/requests";
    }
}
