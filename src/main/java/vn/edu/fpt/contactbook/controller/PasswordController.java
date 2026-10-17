package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.ChangePasswordForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.security.SecurityContextRefresher;
import vn.edu.fpt.contactbook.service.AccountService;
import vn.edu.fpt.contactbook.service.PasswordPolicy;

/**
 * Đổi mật khẩu: dùng cho lần bắt buộc (must_change_password = 1) và khi người dùng tự đổi.
 */
@Controller
@RequiredArgsConstructor
public class PasswordController {

    private static final String VIEW = "auth/change-password";

    private final AccountService accountService;
    private final SecurityContextRefresher securityContextRefresher;

    @GetMapping("/change-password")
    public String form(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("form", new ChangePasswordForm());
        return prepare(principal, model);
    }

    @PostMapping("/change-password")
    public String change(@AuthenticationPrincipal AppUserPrincipal principal,
                         @Valid @ModelAttribute("form") ChangePasswordForm form, BindingResult bindingResult,
                         Model model, HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            PasswordPolicy.validate(form.getCurrentPassword(), form.getNewPassword(), form.getConfirmPassword())
                    .forEach(v -> bindingResult.rejectValue(v.field(), "password.policy." + v.name(), v.message()));
        }
        if (!bindingResult.hasErrors()) {
            try {
                accountService.changePassword(principal.getUserId(), form.getCurrentPassword(),
                        form.getNewPassword(), form.getConfirmPassword(), request.getRemoteAddr());
            } catch (BusinessException e) {
                bindingResult.rejectValue(e.getField() == null ? "currentPassword" : e.getField(),
                        "password.business", e.getMessage());
            }
        }
        if (bindingResult.hasErrors()) {
            return prepare(principal, model);
        }
        securityContextRefresher.replacePrincipal(principal.withPasswordChanged(), request, response);
        redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu thành công");
        return "redirect:/";
    }

    private String prepare(AppUserPrincipal principal, Model model) {
        model.addAttribute("forced", principal.isMustChangePassword());
        model.addAttribute("activeMenu", "password");
        return VIEW;
    }
}
