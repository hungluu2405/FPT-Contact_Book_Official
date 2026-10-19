package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.ActivityForm;
import vn.edu.fpt.contactbook.dto.ActivityView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ClubActivityService;
import vn.edu.fpt.contactbook.service.ClubService;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.util.List;

/**
 * Hoạt động câu lạc bộ (SRS FR-CLUB-05..07): danh sách, tạo, sửa, hủy, ghi nhận tham dự (chủ nhiệm, phó chủ nhiệm,
 * ADMIN); đăng ký và hủy đăng ký (thành viên ACTIVE). Quyền và luật thời gian kiểm tra ở ClubActivityService.
 */
@Controller
@RequestMapping(ClubActivityController.BASE)
@RequiredArgsConstructor
public class ClubActivityController {

    static final String BASE = "/clubs/{clubId:\\d+}/activities";

    private final ClubActivityService activityService;
    private final ClubService clubService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<ActivityView> items = activityService.all(actor, clubId, page);
        model.addAttribute("detail", clubService.view(actor, clubId));
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, base(clubId), PageUtils.params()));
        model.addAttribute("activeMenu", "clubs");
        return "clubs/activities";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                             Model model) {
        model.addAttribute("form", activityService.newForm(actor, clubId));
        return formView(actor, clubId, null, model);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                         @Valid @ModelAttribute("form") ActivityForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                activityService.create(actor, clubId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã tạo hoạt động");
                return "redirect:" + base(clubId);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, clubId, null, model);
    }

    @GetMapping("/{activityId:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                           @PathVariable Long activityId, Model model) {
        model.addAttribute("form", activityService.editForm(actor, clubId, activityId));
        return formView(actor, clubId, activityId, model);
    }

    @PostMapping("/{activityId:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                       @PathVariable Long activityId, @Valid @ModelAttribute("form") ActivityForm form,
                       BindingResult bindingResult, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                activityService.update(actor, clubId, activityId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu hoạt động");
                return "redirect:" + base(clubId);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, clubId, activityId, model);
    }

    @PostMapping("/{activityId:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                         @PathVariable Long activityId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(base(clubId), redirectAttributes, "Đã hủy hoạt động",
                () -> activityService.cancel(actor, clubId, activityId, request.getRemoteAddr()));
    }

    @PostMapping("/{activityId:\\d+}/register")
    public String register(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                           @PathVariable Long activityId, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(ClubController.BASE + "/" + clubId, redirectAttributes,
                "Đã đăng ký hoạt động", () -> activityService.register(actor, clubId, activityId));
    }

    @PostMapping("/{activityId:\\d+}/unregister")
    public String unregister(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                             @PathVariable Long activityId, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(ClubController.BASE + "/" + clubId, redirectAttributes,
                "Đã hủy đăng ký hoạt động", () -> activityService.unregister(actor, clubId, activityId));
    }

    @GetMapping("/{activityId:\\d+}/attendance")
    public String attendance(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                             @PathVariable Long activityId, Model model) {
        model.addAttribute("sheet", activityService.attendanceSheet(actor, clubId, activityId));
        model.addAttribute("activeMenu", "clubs");
        return "clubs/attendance";
    }

    @PostMapping("/{activityId:\\d+}/attendance")
    public String saveAttendance(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long clubId,
                                 @PathVariable Long activityId,
                                 @RequestParam(value = "attended", required = false) List<Long> attended,
                                 HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(base(clubId) + "/" + activityId + "/attendance", redirectAttributes,
                "Đã lưu danh sách tham dự",
                () -> activityService.markAttendance(actor, clubId, activityId, attended, request.getRemoteAddr()));
    }

    private String formView(AppUserPrincipal actor, Long clubId, Long activityId, Model model) {
        model.addAttribute("detail", clubService.view(actor, clubId));
        model.addAttribute("activityId", activityId);
        model.addAttribute("activeMenu", "clubs");
        return "clubs/activity-form";
    }

    private static String base(Long clubId) {
        return ClubController.BASE + "/" + clubId + "/activities";
    }
}
