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
import vn.edu.fpt.contactbook.dto.SemesterForm;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.SemesterService;
import vn.edu.fpt.contactbook.util.PageUtils;

/**
 * Danh mục học kỳ: danh sách, thêm, xem, sửa, đặt làm học kỳ hiện tại. Chỉ ADMIN.
 */
@Controller
@RequestMapping(SemesterController.BASE)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SemesterController {

    static final String BASE = "/admin/catalog/semesters";
    private static final String MENU = "catalog-semesters";

    private final SemesterService semesterService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("filter") CatalogFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<SemesterView> items = semesterService.list(actor, filter, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE,
                PageUtils.params("keyword", filter.getKeyword())));
        model.addAttribute("activeMenu", MENU);
        return "admin/catalog/semesters/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new SemesterForm());
        return formView(model, null);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,
                         @Valid @ModelAttribute("form") SemesterForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = semesterService.create(actor, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm học kỳ");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(model, null);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("item", semesterService.detail(actor, id));
        model.addAttribute("activeMenu", MENU);
        return "admin/catalog/semesters/detail";
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", semesterService.editForm(actor, id));
        return formView(model, id);
    }

    @PostMapping("/{id:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") SemesterForm form, BindingResult bindingResult, Model model,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                semesterService.update(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu học kỳ");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(model, id);
    }

    @PostMapping("/{id:\\d+}/set-current")
    public String setCurrent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE, redirectAttributes, "Đã đặt học kỳ hiện tại",
                () -> semesterService.setCurrent(actor, id, request.getRemoteAddr()));
    }

    /** id = null khi thêm mới. */
    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("activeMenu", MENU);
        return "admin/catalog/semesters/form";
    }
}
