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
import vn.edu.fpt.contactbook.dto.RoleAssignForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.UserRoleService;
import vn.edu.fpt.contactbook.service.UserService;

/**
 * Gán và gỡ vai trò (từng cặp vai trò, bộ môn). Chỉ ADMIN.
 */
@Controller
@RequestMapping("/admin/users/{id:\\d+}/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserService userService;
    private final UserRoleService userRoleService;
    private final CatalogLookupService catalogLookupService;

    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", new RoleAssignForm());
        return view(actor, id, model);
    }

    @PostMapping
    public String assign(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @Valid @ModelAttribute("form") RoleAssignForm form, BindingResult bindingResult,
                         Model model, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                userRoleService.assign(actor, id, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã gán vai trò");
                return "redirect:/admin/users/" + id + "/roles";
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return view(actor, id, model);
    }

    @PostMapping("/{userRoleId:\\d+}/remove")
    public String remove(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long userRoleId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect("/admin/users/" + id + "/roles", redirectAttributes, "Đã gỡ vai trò",
                () -> userRoleService.remove(actor, id, userRoleId, request.getRemoteAddr()));
    }

    private String view(AppUserPrincipal actor, Long id, Model model) {
        model.addAttribute("user", userService.detail(actor, id));
        model.addAttribute("roleCodes", UserService.ROLE_ORDER);
        model.addAttribute("studentStatuses", StudentStatus.values());
        model.addAttribute("departments", catalogLookupService.departments());
        model.addAttribute("majors", catalogLookupService.majors());
        model.addAttribute("classes", catalogLookupService.classes());
        model.addAttribute("activeMenu", "users");
        return "admin/users/roles";
    }
}
