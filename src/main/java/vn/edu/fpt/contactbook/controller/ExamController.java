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
import vn.edu.fpt.contactbook.dto.ExamForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.model.enums.ExamType;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ClassSectionService;
import vn.edu.fpt.contactbook.service.ExamService;

/**
 * Lịch thi của lớp học phần: thêm, sửa, xóa. ADMIN và trưởng bộ môn của môn (phạm vi kiểm tra ở service).
 */
@Controller
@RequestMapping(ClassSectionController.BASE + "/{id:\\d+}/exams")
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ClassSectionService classSectionService;
    private final CatalogLookupService catalogLookupService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", new ExamForm());
        return formView(actor, id, null, model);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @Valid @ModelAttribute("form") ExamForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                examService.create(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm lịch thi");
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

    @GetMapping("/{examId:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                           @PathVariable Long examId, Model model) {
        model.addAttribute("form", examService.editForm(actor, id, examId));
        return formView(actor, id, examId, model);
    }

    @PostMapping("/{examId:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @PathVariable Long examId, @Valid @ModelAttribute("form") ExamForm form,
                       BindingResult bindingResult, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        // Kiểm tra phạm vi trước, kể cả khi form có lỗi.
        examService.editForm(actor, id, examId);
        if (!bindingResult.hasErrors()) {
            try {
                examService.update(actor, id, examId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu lịch thi");
                return "redirect:" + detail(id);
            } catch (ScheduleConflictException e) {
                model.addAttribute("conflicts", e.getConflicts());
                FormErrors.apply(bindingResult, e);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, id, examId, model);
    }

    @PostMapping("/{examId:\\d+}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long examId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(detail(id), redirectAttributes, "Đã xóa lịch thi",
                () -> examService.delete(actor, id, examId, request.getRemoteAddr()));
    }

    private String formView(AppUserPrincipal actor, Long id, Long examId, Model model) {
        model.addAttribute("item", classSectionService.detail(actor, id));
        model.addAttribute("examId", examId);
        model.addAttribute("examTypes", ExamType.values());
        model.addAttribute("rooms", catalogLookupService.rooms());
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/exam-form";
    }

    private static String detail(Long id) {
        return ClassSectionController.BASE + "/" + id;
    }
}
