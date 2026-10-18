package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
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
import vn.edu.fpt.contactbook.dto.ClassSectionForm;
import vn.edu.fpt.contactbook.dto.ClassSectionView;
import vn.edu.fpt.contactbook.dto.EnrollmentRow;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.SectionFilter;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ClassSectionService;
import vn.edu.fpt.contactbook.service.EnrollmentService;
import vn.edu.fpt.contactbook.service.SubjectAccessPolicy;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.util.List;
import java.util.Set;

/**
 * Lớp học phần: danh sách, thêm, xem (kèm danh sách sinh viên), sửa. Mở cho ADMIN và HEAD_OF_DEPARTMENT;
 * phạm vi bộ môn kiểm tra ở ClassSectionService. Ghi danh nằm ở EnrollmentController (chỉ ADMIN).
 */
@Controller
@RequestMapping(ClassSectionController.BASE)
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class ClassSectionController {

    static final String BASE = "/admin/sections";
    static final String MENU = "catalog-sections";
    /** Nút "Đề xuất mã" gửi form với action=suggest: điền mã rồi hiện lại form, không lưu. */
    private static final String SUGGEST = "suggest";

    private final ClassSectionService classSectionService;
    private final EnrollmentService enrollmentService;
    private final SubjectAccessPolicy accessPolicy;
    private final CatalogLookupService catalogLookupService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("filter") SectionFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<ClassSectionView> items = classSectionService.list(actor, filter, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE, PageUtils.params("keyword", filter.getKeyword(),
                "semesterId", filter.getSemesterId(), "status", filter.getStatus())));
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("statuses", SectionStatus.values());
        model.addAttribute("activeMenu", MENU);
        return "admin/sections/list";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        accessPolicy.requireCanManage(actor);
        model.addAttribute("form", new ClassSectionForm());
        return formView(actor, model, null);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,
                         @Valid @ModelAttribute("form") ClassSectionForm form, BindingResult bindingResult,
                         @RequestParam(required = false) String action, Model model, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (SUGGEST.equals(action)) {
            return suggest(actor, form, bindingResult, model, null);
        }
        if (!bindingResult.hasErrors()) {
            try {
                Long id = classSectionService.create(actor, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm lớp học phần");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, model, null);
    }

    /**
     * Chi tiết lớp và danh sách sinh viên (lọc theo trạng thái, mặc định đang học). ADMIN thấy thêm khối
     * ghi danh: tìm sinh viên theo mã/tên (q), ghi danh cả lớp hành chính, nhập Excel.
     */
    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        ClassSectionView section = classSectionService.detail(actor, id);
        EnrollmentStatus statusFilter = "ALL".equals(status) ? null
                : status == null ? EnrollmentStatus.ENROLLED : parseStatus(status);
        Page<EnrollmentRow> students = enrollmentService.enrollments(actor, id, statusFilter, page);
        model.addAttribute("item", section);
        model.addAttribute("students", students);
        model.addAttribute("statusFilter", statusFilter == null ? "ALL" : statusFilter.name());
        model.addAttribute("enrollmentStatuses", EnrollmentStatus.values());
        model.addAttribute("pager", PageUtils.pager(students, BASE + "/" + id,
                PageUtils.params("status", status, "q", q)));
        boolean admin = accessPolicy.isAdmin(actor);
        model.addAttribute("canEnroll", admin);
        if (admin) {
            model.addAttribute("q", q);
            model.addAttribute("candidates", q == null ? List.of() : enrollmentService.candidates(actor, id, q));
            model.addAttribute("classes", catalogLookupService.classes());
        }
        model.addAttribute("activeMenu", MENU);
        return "admin/sections/detail";
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", classSectionService.editForm(actor, id));
        return formView(actor, model, id);
    }

    @PostMapping("/{id:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") ClassSectionForm form, BindingResult bindingResult,
                       @RequestParam(required = false) String action, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        // Kiểm tra phạm vi trước, kể cả khi form có lỗi hoặc chỉ đề xuất mã.
        classSectionService.detail(actor, id);
        if (SUGGEST.equals(action)) {
            return suggest(actor, form, bindingResult, model, id);
        }
        if (!bindingResult.hasErrors()) {
            try {
                classSectionService.update(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu lớp học phần");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, model, id);
    }

    /** Điền mã đề xuất vào form và hiện lại; lỗi Bean Validation của các ô khác chưa cần báo lúc này. */
    private String suggest(AppUserPrincipal actor, ClassSectionForm form, BindingResult bindingResult, Model model,
                           Long id) {
        BindingResult clean = new BeanPropertyBindingResult(form, "form");
        try {
            form.setCode(classSectionService.suggestCode(actor, form.getSubjectId(), form.getSemesterId()));
        } catch (BusinessException e) {
            FormErrors.apply(clean, e);
        }
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", clean);
        return formView(actor, model, id);
    }

    private static EnrollmentStatus parseStatus(String status) {
        try {
            return EnrollmentStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return EnrollmentStatus.ENROLLED;
        }
    }

    /** ADMIN chọn mọi môn và giảng viên; trưởng bộ môn chỉ thấy môn và giảng viên của bộ môn mình. */
    private String formView(AppUserPrincipal actor, Model model, Long id) {
        boolean admin = accessPolicy.isAdmin(actor);
        Set<Long> scope = accessPolicy.headDepartmentIds(actor);
        List<OptionView> subjects = catalogLookupService.subjects().stream()
                .filter(o -> admin || scope.contains(o.groupId())).toList();
        List<OptionView> teachers = catalogLookupService.teachers().stream()
                .filter(o -> admin || scope.contains(o.groupId())).toList();
        model.addAttribute("id", id);
        model.addAttribute("subjects", subjects);
        model.addAttribute("teachers", teachers);
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("statuses", SectionStatus.values());
        model.addAttribute("activeMenu", MENU);
        return "admin/sections/form";
    }
}
