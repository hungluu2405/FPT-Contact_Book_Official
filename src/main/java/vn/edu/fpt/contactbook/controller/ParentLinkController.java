package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import vn.edu.fpt.contactbook.dto.ParentLinkForm;
import vn.edu.fpt.contactbook.dto.UserDetailView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ParentLinkService;
import vn.edu.fpt.contactbook.service.UserService;

/**
 * Liên kết phụ huynh – sinh viên. /parents trên trang của sinh viên, /children trên trang của phụ huynh.
 * Chỉ ADMIN.
 */
@Controller
@RequestMapping("/admin/users/{id:\\d+}")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ParentLinkController {

    private static final String PARENTS = "parents";
    private static final String CHILDREN = "children";

    private final UserService userService;
    private final ParentLinkService parentLinkService;

    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/parents")
    public String parents(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model,
                          RedirectAttributes redirectAttributes) {
        return page(actor, id, PARENTS, new ParentLinkForm(), model, redirectAttributes);
    }

    @GetMapping("/children")
    public String children(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model,
                           RedirectAttributes redirectAttributes) {
        return page(actor, id, CHILDREN, new ParentLinkForm(), model, redirectAttributes);
    }

    @PostMapping("/parents")
    public String addParent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                            @ModelAttribute("form") ParentLinkForm form, BindingResult bindingResult, Model model,
                            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            parentLinkService.linkParent(actor, id, form, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "Đã liên kết phụ huynh");
            return "redirect:/admin/users/" + id + "/" + PARENTS;
        } catch (BusinessException e) {
            FormErrors.apply(bindingResult, e);
            return page(actor, id, PARENTS, form, model, redirectAttributes);
        }
    }

    @PostMapping("/children")
    public String addChild(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                           @ModelAttribute("form") ParentLinkForm form, BindingResult bindingResult, Model model,
                           HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            parentLinkService.linkChild(actor, id, form, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "Đã liên kết sinh viên");
            return "redirect:/admin/users/" + id + "/" + CHILDREN;
        } catch (BusinessException e) {
            FormErrors.apply(bindingResult, e);
            return page(actor, id, CHILDREN, form, model, redirectAttributes);
        }
    }

    @PostMapping("/parents/{parentId:\\d+}/remove")
    public String removeParent(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                               @PathVariable Long parentId, HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        return run(id, PARENTS, redirectAttributes, "Đã gỡ liên kết",
                () -> parentLinkService.unlinkParent(actor, id, parentId, request.getRemoteAddr()));
    }

    @PostMapping("/parents/{parentId:\\d+}/primary")
    public String setPrimary(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                             @PathVariable Long parentId, HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        return run(id, PARENTS, redirectAttributes, "Đã đặt phụ huynh chính",
                () -> parentLinkService.setPrimaryParent(actor, id, parentId, request.getRemoteAddr()));
    }

    @PostMapping("/children/{studentId:\\d+}/remove")
    public String removeChild(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                              @PathVariable Long studentId, HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        return run(id, CHILDREN, redirectAttributes, "Đã gỡ liên kết",
                () -> parentLinkService.unlinkChild(actor, id, studentId, request.getRemoteAddr()));
    }

    /** mode = parents (trang sinh viên) hoặc children (trang phụ huynh); người dùng phải có hồ sơ tương ứng. */
    private String page(AppUserPrincipal actor, Long id, String mode, ParentLinkForm form, Model model,
                        RedirectAttributes redirectAttributes) {
        UserDetailView user = userService.detail(actor, id);
        boolean hasProfile = PARENTS.equals(mode) ? user.student() != null : user.parent() != null;
        if (!hasProfile) {
            redirectAttributes.addFlashAttribute("errorMessage", PARENTS.equals(mode)
                    ? "Người dùng này chưa có hồ sơ sinh viên" : "Người dùng này chưa có hồ sơ phụ huynh");
            return "redirect:/admin/users/" + id;
        }
        model.addAttribute("user", user);
        model.addAttribute("mode", mode);
        model.addAttribute("form", form);
        model.addAttribute("relationships", ParentRelationship.values());
        model.addAttribute("links", PARENTS.equals(mode) ? user.parents() : user.children());
        model.addAttribute("activeMenu", "users");
        return "admin/users/links";
    }

    private static String run(Long id, String mode, RedirectAttributes redirectAttributes, String success,
                              Runnable action) {
        return FormErrors.runThenRedirect("/admin/users/" + id + "/" + mode, redirectAttributes, success, action);
    }
}
