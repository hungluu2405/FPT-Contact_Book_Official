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
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.AttendanceViewService;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.util.PageUtils;

/**
 * Lớp giảng dạy và điểm danh của giảng viên (SRS FR-ATT-01..03, UC-04): chỉ lớp có class_sections.teacher_id là hồ
 * sơ giảng viên của người đăng nhập; lớp khác, kể cả gõ URL, nhận 403 (kiểm tra ở service).
 */
@Controller
@RequestMapping(TeacherAttendanceController.BASE)
@RequiredArgsConstructor
public class TeacherAttendanceController {

    static final String BASE = "/teacher/sections";
    private static final String MENU = "teacher-sections";

    private final AttendanceViewService attendanceViewService;
    private final AttendancePages attendancePages;
    private final CatalogLookupService catalogLookupService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(required = false) Long semesterId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        InSemester<Page<TeacherSectionView>> result = attendanceViewService.teacherSections(actor, semesterId, page);
        model.addAttribute("semester", result.semester());
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("items", result.data());
        model.addAttribute("pager", PageUtils.pager(result.data(), BASE,
                PageUtils.params("semesterId", result.semester().id())));
        model.addAttribute("activeMenu", MENU);
        return "teacher/sections";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(defaultValue = "0") int sessionPage,
                         @RequestParam(defaultValue = "0") int studentPage, Model model) {
        model.addAttribute("activeMenu", MENU);
        model.addAttribute("backUrl", BASE);
        return attendancePages.section(model, actor, Access.TEACHER, id, sessionPage, studentPage, base(id),
                base(id), true, true);
    }

    @GetMapping("/{id:\\d+}/sessions/{sessionId:\\d+}/attendance")
    public String sheet(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        @PathVariable Long sessionId, Model model) {
        model.addAttribute("activeMenu", MENU);
        model.addAttribute("actionUrl", sheetUrl(id, sessionId));
        return attendancePages.sheet(model, actor, Access.TEACHER, id, sessionId, base(id), true);
    }

    @PostMapping("/{id:\\d+}/sessions/{sessionId:\\d+}/attendance")
    public String save(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @PathVariable Long sessionId, @Valid @ModelAttribute("form") AttendanceForm form,
                       BindingResult bindingResult, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        model.addAttribute("activeMenu", MENU);
        model.addAttribute("actionUrl", sheetUrl(id, sessionId));
        return attendancePages.save(model, actor, Access.TEACHER, id, sessionId, form, bindingResult,
                request.getRemoteAddr(), base(id), true, redirectAttributes);
    }

    private static String base(Long id) {
        return BASE + "/" + id;
    }

    private static String sheetUrl(Long id, Long sessionId) {
        return base(id) + "/sessions/" + sessionId + "/attendance";
    }
}
