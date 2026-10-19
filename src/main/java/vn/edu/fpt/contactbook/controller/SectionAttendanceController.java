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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;

/**
 * Điểm danh từ trang quản lý lớp học phần: ADMIN xem mọi lớp và điều chỉnh buổi đã điểm danh (FR-ATT-03); trưởng bộ
 * môn chỉ xem lớp thuộc bộ môn mình (FR-ATT-02). Phạm vi và quyền ghi kiểm tra ở service.
 */
@Controller
@RequestMapping(ClassSectionController.BASE + "/{id:\\d+}")
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class SectionAttendanceController {

    private final AttendancePages attendancePages;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/attendance")
    public String stats(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        @RequestParam(defaultValue = "0") int sessionPage,
                        @RequestParam(defaultValue = "0") int studentPage, Model model) {
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        model.addAttribute("backUrl", detail(id));
        return attendancePages.section(model, actor, Access.MANAGER, id, sessionPage, studentPage, detail(id),
                stats(id), false, actor.hasRole(RoleCodes.ADMIN));
    }

    @GetMapping("/sessions/{sessionId:\\d+}/attendance")
    public String sheet(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        @PathVariable Long sessionId, Model model) {
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        model.addAttribute("actionUrl", sheetUrl(id, sessionId));
        return attendancePages.sheet(model, actor, Access.MANAGER, id, sessionId, stats(id),
                actor.hasRole(RoleCodes.ADMIN));
    }

    @PostMapping("/sessions/{sessionId:\\d+}/attendance")
    public String save(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @PathVariable Long sessionId, @Valid @ModelAttribute("form") AttendanceForm form,
                       BindingResult bindingResult, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        model.addAttribute("actionUrl", sheetUrl(id, sessionId));
        return attendancePages.save(model, actor, Access.MANAGER, id, sessionId, form, bindingResult,
                request.getRemoteAddr(), stats(id), actor.hasRole(RoleCodes.ADMIN), redirectAttributes);
    }

    private static String detail(Long id) {
        return ClassSectionController.BASE + "/" + id;
    }

    private static String stats(Long id) {
        return detail(id) + "/attendance";
    }

    private static String sheetUrl(Long id, Long sessionId) {
        return detail(id) + "/sessions/" + sessionId + "/attendance";
    }
}
