package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import vn.edu.fpt.contactbook.dto.CreatedUser;
import vn.edu.fpt.contactbook.dto.UserFilter;
import vn.edu.fpt.contactbook.dto.UserForm;
import vn.edu.fpt.contactbook.dto.UserSummaryView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.Gender;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.security.SecurityContextRefresher;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.UserAccessPolicy;
import vn.edu.fpt.contactbook.service.UserService;
import vn.edu.fpt.contactbook.util.PageUtils;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.util.List;

/**
 * Quản lý người dùng: danh sách, thêm, xem, sửa, khóa/mở khóa, đặt lại mật khẩu.
 * Danh sách và chi tiết mở cho HEAD_OF_DEPARTMENT (phạm vi kiểm tra ở UserService); còn lại chỉ ADMIN.
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserAccessPolicy accessPolicy;
    private final CatalogLookupService catalogLookupService;
    private final AppUserDetailsService userDetailsService;
    private final SecurityContextRefresher securityContextRefresher;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("filter") UserFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<UserSummaryView> users = userService.list(actor, filter, page);
        boolean admin = accessPolicy.isAdmin(actor);
        model.addAttribute("users", users);
        model.addAttribute("pager", PageUtils.pager(users, "/admin/users", PageUtils.params(
                "keyword", filter.getKeyword(), "role", filter.getRole(), "departmentId", filter.getDepartmentId(),
                "classId", filter.getClassId(), "status", filter.getStatus())));
        model.addAttribute("isAdmin", admin);
        model.addAttribute("roleCodes", UserService.ROLE_ORDER);
        model.addAttribute("departments", admin ? catalogLookupService.departments()
                : catalogLookupService.departments(accessPolicy.headDepartmentIds(actor)));
        model.addAttribute("classes", admin ? catalogLookupService.classes() : List.of());
        model.addAttribute("activeMenu", "users");
        return "admin/users/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("form", new UserForm());
        return formView(model, null);
    }

    @PostMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,
                         @Valid @ModelAttribute("form") UserForm form, BindingResult bindingResult, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                CreatedUser created = userService.create(actor, form, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("temporaryPassword", created.temporaryPassword());
                redirectAttributes.addFlashAttribute("successMessage", "Đã tạo người dùng");
                return "redirect:/admin/users/" + created.userId();
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(model, null);
    }

    /** Có thể đang hiện mật khẩu tạm (flash) nên không cho trình duyệt hay proxy lưu lại trang. */
    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model,
                         HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("user", userService.detail(actor, id));
        model.addAttribute("isSelf", id.equals(actor.getUserId()));
        model.addAttribute("activeMenu", "users");
        return "admin/users/detail";
    }

    @GetMapping("/{id:\\d+}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", userService.editForm(actor, id));
        return formView(model, userService.detail(actor, id));
    }

    @PostMapping("/{id:\\d+}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") UserForm form, BindingResult bindingResult, Model model,
                       HttpServletRequest request, HttpServletResponse response,
                       RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                userService.update(actor, id, form, request.getRemoteAddr());
                if (id.equals(actor.getUserId())) {
                    refreshOwnPrincipal(form.getPhone(), request, response);
                }
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu thông tin người dùng");
                return "redirect:/admin/users/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(model, userService.detail(actor, id));
    }

    @PostMapping("/{id:\\d+}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public String lock(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect("/admin/users/" + id, redirectAttributes, "Đã khóa tài khoản",
                () -> userService.lock(actor, id, request.getRemoteAddr()));
    }

    @PostMapping("/{id:\\d+}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public String unlock(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect("/admin/users/" + id, redirectAttributes, "Đã mở khóa tài khoản",
                () -> userService.unlock(actor, id, request.getRemoteAddr()));
    }

    @PostMapping("/{id:\\d+}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public String resetPassword(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                HttpServletRequest request, RedirectAttributes redirectAttributes) {
        CreatedUser reset = userService.resetPassword(actor, id, request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("temporaryPassword", reset.temporaryPassword());
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã đặt lại mật khẩu. Người dùng phải đổi mật khẩu ở lần đăng nhập kế tiếp");
        return "redirect:/admin/users/" + id;
    }

    private void refreshOwnPrincipal(String phone, HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal fresh = userDetailsService.loadUserByUsername(PhoneUtils.normalize(phone).orElseThrow());
        fresh.eraseCredentials();
        securityContextRefresher.replacePrincipal(fresh, request, response);
    }

    /** user = null khi thêm mới; khi sửa dùng để biết người dùng đang có hồ sơ nào. */
    private String formView(Model model, Object user) {
        model.addAttribute("user", user);
        model.addAttribute("roleCodes", UserService.ROLE_ORDER);
        model.addAttribute("genders", Gender.values());
        model.addAttribute("studentStatuses", StudentStatus.values());
        model.addAttribute("departments", catalogLookupService.departments());
        model.addAttribute("majors", catalogLookupService.majors());
        model.addAttribute("classes", catalogLookupService.classes());
        model.addAttribute("activeMenu", "users");
        return "admin/users/form";
    }
}
