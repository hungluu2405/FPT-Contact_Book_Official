package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.GradeAdjustForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.GradeService;

/**
 * Bảng điểm lớp học phần cho trưởng bộ môn (bộ môn mình) và ADMIN: xem cả ô chưa công bố, công bố (FR-GRADE-04),
 * điều chỉnh ô đã công bố (FR-GRADE-06). Phạm vi kiểm tra ở GradeService.
 */
@Controller
@RequestMapping(ClassSectionController.BASE + "/{id:\\d+}/grades")
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class SectionGradeController {

    private final GradeService gradeService;

    @GetMapping
    public String sheet(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("sheet", gradeService.sheet(actor, Access.MANAGER, id));
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/grades";
    }

    @PostMapping("/publish")
    public String publish(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            int count = gradeService.publish(actor, id, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "Đã công bố " + count + " ô điểm");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + base(id);
    }

    @GetMapping("/{gradeId:\\d+}/adjust")
    public String adjustForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                             @PathVariable Long gradeId, Model model) {
        model.addAttribute("form", new GradeAdjustForm());
        return adjustView(actor, id, gradeId, model);
    }

    @PostMapping("/{gradeId:\\d+}/adjust")
    public String adjust(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long gradeId, @Valid @ModelAttribute("form") GradeAdjustForm form,
                         BindingResult bindingResult, Model model, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        // Kiểm tra phạm vi và ô điểm trước, kể cả khi form có lỗi.
        gradeService.adjustView(actor, id, gradeId);
        if (!bindingResult.hasErrors()) {
            try {
                gradeService.adjust(actor, id, gradeId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã điều chỉnh điểm");
                return "redirect:" + base(id);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return adjustView(actor, id, gradeId, model);
    }

    private String adjustView(AppUserPrincipal actor, Long id, Long gradeId, Model model) {
        model.addAttribute("grade", gradeService.adjustView(actor, id, gradeId));
        model.addAttribute("sectionId", id);
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/grade-adjust";
    }

    private static String base(Long id) {
        return ClassSectionController.BASE + "/" + id + "/grades";
    }
}
