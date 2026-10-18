package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.SessionForm;
import vn.edu.fpt.contactbook.dto.WeeklyScheduleForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ClassSectionService;
import vn.edu.fpt.contactbook.service.ClassSessionService;

import java.time.DayOfWeek;

/**
 * Buổi học của lớp học phần: thêm buổi lẻ, tạo lịch hàng tuần, sửa, hủy. ADMIN và trưởng bộ môn của môn
 * (phạm vi kiểm tra ở service). Lịch bị trùng thì hiện lại form kèm bảng xung đột.
 */
@Controller
@RequestMapping(ClassSectionController.BASE + "/{id:\\d+}/sessions")
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassSessionService classSessionService;
    private final ClassSectionService classSectionService;
    private final CatalogLookupService catalogLookupService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", new SessionForm());
        return formView(actor, id, null, model);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @Valid @ModelAttribute("form") SessionForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                classSessionService.create(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm buổi học bù");
                return "redirect:" + detail(id);
            } catch (ScheduleConflictException e) {
                model.addAttribute("conflicts", e.getConflicts());
                FormErrors.apply(bindingResult, e);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, id, null, model);
    }

    @GetMapping("/{sessionId:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                           @PathVariable Long sessionId, Model model) {
        model.addAttribute("form", classSessionService.editForm(actor, id, sessionId));
        return formView(actor, id, sessionId, model);
    }

    @PostMapping("/{sessionId:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @PathVariable Long sessionId, @Valid @ModelAttribute("form") SessionForm form,
                       BindingResult bindingResult, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        // Kiểm tra phạm vi trước, kể cả khi form có lỗi.
        classSessionService.editForm(actor, id, sessionId);
        if (!bindingResult.hasErrors()) {
            try {
                classSessionService.update(actor, id, sessionId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu buổi học");
                return "redirect:" + detail(id);
            } catch (ScheduleConflictException e) {
                model.addAttribute("conflicts", e.getConflicts());
                FormErrors.apply(bindingResult, e);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, id, sessionId, model);
    }

    @PostMapping("/{sessionId:\\d+}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long sessionId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(detail(id), redirectAttributes, "Đã hủy buổi học",
                () -> classSessionService.cancel(actor, id, sessionId, request.getRemoteAddr()));
    }

    @GetMapping("/weekly")
    public String weeklyForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", new WeeklyScheduleForm());
        return weeklyView(actor, id, model);
    }

    @PostMapping("/weekly")
    public String weekly(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @Valid @ModelAttribute("form") WeeklyScheduleForm form, BindingResult bindingResult,
                         Model model, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                int created = classSessionService.createWeekly(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã tạo " + created + " buổi học");
                return "redirect:" + detail(id);
            } catch (ScheduleConflictException e) {
                model.addAttribute("conflicts", e.getConflicts());
                FormErrors.apply(bindingResult, e);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return weeklyView(actor, id, model);
    }

    private String formView(AppUserPrincipal actor, Long id, Long sessionId, Model model) {
        model.addAttribute("item", classSectionService.detail(actor, id));
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("rooms", catalogLookupService.rooms());
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/session-form";
    }

    private String weeklyView(AppUserPrincipal actor, Long id, Model model) {
        model.addAttribute("item", classSectionService.detail(actor, id));
        model.addAttribute("weekdays", DayOfWeek.values());
        model.addAttribute("rooms", catalogLookupService.rooms());
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/weekly";
    }

    private static String detail(Long id) {
        return ClassSectionController.BASE + "/" + id;
    }
}
