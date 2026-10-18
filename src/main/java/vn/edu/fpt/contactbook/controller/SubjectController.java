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
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.GradeComponentForm;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.SubjectForm;
import vn.edu.fpt.contactbook.dto.SubjectView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.GradeComponentService;
import vn.edu.fpt.contactbook.service.SubjectAccessPolicy;
import vn.edu.fpt.contactbook.service.SubjectService;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.util.List;

/**
 * Môn học và đầu điểm: danh sách, thêm, xem, sửa; thêm/sửa/xóa đầu điểm trong trang chi tiết.
 * Mở cho ADMIN và HEAD_OF_DEPARTMENT; phạm vi bộ môn kiểm tra ở SubjectService.
 */
@Controller
@RequestMapping(SubjectController.BASE)
@PreAuthorize("hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT')")
@RequiredArgsConstructor
public class SubjectController {

    static final String BASE = "/admin/subjects";
    private static final String MENU = "catalog-subjects";

    private final SubjectService subjectService;
    private final GradeComponentService gradeComponentService;
    private final SubjectAccessPolicy accessPolicy;
    private final CatalogLookupService catalogLookupService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("filter") CatalogFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<SubjectView> items = subjectService.list(actor, filter, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE,
                PageUtils.params("keyword", filter.getKeyword(), "groupId", filter.getGroupId())));
        model.addAttribute("groups", departments(actor));
        model.addAttribute("activeMenu", MENU);
        return "admin/subjects/list";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        accessPolicy.requireCanManage(actor);
        SubjectForm form = new SubjectForm();
        List<OptionView> departments = departments(actor);
        if (departments.size() == 1) {
            form.setDepartmentId(departments.getFirst().id());
        }
        model.addAttribute("form", form);
        return formView(actor, model, null);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,
                         @Valid @ModelAttribute("form") SubjectForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = subjectService.create(actor, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm môn học");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, model, null);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("componentForm", new GradeComponentForm());
        return detailView(actor, id, model);
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", subjectService.editForm(actor, id));
        return formView(actor, model, id);
    }

    @PostMapping("/{id:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") SubjectForm form, BindingResult bindingResult, Model model,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                subjectService.update(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu môn học");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        // Kiểm tra phạm vi trước khi hiện lại form, kể cả khi form có lỗi.
        subjectService.detail(actor, id);
        return formView(actor, model, id);
    }

    @PostMapping("/{id:\\d+}/components")
    public String addComponent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                               @Valid @ModelAttribute("componentForm") GradeComponentForm form,
                               BindingResult bindingResult, Model model, HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                gradeComponentService.create(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm đầu điểm");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return detailView(actor, id, model);
    }

    @GetMapping("/{id:\\d+}/components/{componentId:\\d+}/edit")
    public String editComponentForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                    @PathVariable Long componentId, Model model) {
        model.addAttribute("componentForm", gradeComponentService.editForm(actor, id, componentId));
        return componentFormView(actor, id, componentId, model);
    }

    @PostMapping("/{id:\\d+}/components/{componentId:\\d+}/edit")
    public String editComponent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                @PathVariable Long componentId,
                                @Valid @ModelAttribute("componentForm") GradeComponentForm form,
                                BindingResult bindingResult, Model model, HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                gradeComponentService.update(actor, id, componentId, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu đầu điểm");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        gradeComponentService.editForm(actor, id, componentId);
        return componentFormView(actor, id, componentId, model);
    }

    @PostMapping("/{id:\\d+}/components/{componentId:\\d+}/delete")
    public String deleteComponent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                  @PathVariable Long componentId, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes, "Đã xóa đầu điểm",
                () -> gradeComponentService.delete(actor, id, componentId, request.getRemoteAddr()));
    }

    /** ADMIN chọn mọi bộ môn; trưởng bộ môn chỉ thấy bộ môn mình. */
    private List<OptionView> departments(AppUserPrincipal actor) {
        return accessPolicy.isAdmin(actor) ? catalogLookupService.departments()
                : catalogLookupService.departments(accessPolicy.headDepartmentIds(actor));
    }

    private String detailView(AppUserPrincipal actor, Long id, Model model) {
        model.addAttribute("item", subjectService.detail(actor, id));
        model.addAttribute("summary", gradeComponentService.summary(actor, id));
        model.addAttribute("activeMenu", MENU);
        return "admin/subjects/detail";
    }

    private String componentFormView(AppUserPrincipal actor, Long id, Long componentId, Model model) {
        model.addAttribute("item", subjectService.detail(actor, id));
        model.addAttribute("componentId", componentId);
        model.addAttribute("activeMenu", MENU);
        return "admin/subjects/component-form";
    }

    /** id = null khi thêm mới. */
    private String formView(AppUserPrincipal actor, Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("groups", departments(actor));
        model.addAttribute("activeMenu", MENU);
        return "admin/subjects/form";
    }
}
