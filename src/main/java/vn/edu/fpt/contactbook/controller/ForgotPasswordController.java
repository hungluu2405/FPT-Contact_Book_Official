package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import vn.edu.fpt.contactbook.dto.ForgotPasswordForm;
import vn.edu.fpt.contactbook.dto.ResetPasswordForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.PasswordResetService;

import java.util.Map;

/**
 * Quên mật khẩu trên web: bước 1 nhập số điện thoại để nhận mã qua email, bước 2 nhập mã và mật khẩu mới. Số điện
 * thoại chuyển sang bước 2 bằng flash attribute, không đặt lên URL.
 */
@Controller
@RequiredArgsConstructor
public class ForgotPasswordController {

    private static final String REQUEST_VIEW = "auth/forgot-password";
    private static final String RESET_VIEW = "auth/reset-password";

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String requestForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new ForgotPasswordForm());
        return REQUEST_VIEW;
    }

    @PostMapping("/forgot-password")
    public String requestCode(@Valid @ModelAttribute("form") ForgotPasswordForm form, BindingResult bindingResult,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                passwordResetService.requestCode(form.getPhone(), request.getRemoteAddr());
            } catch (BusinessException e) {
                bindingResult.rejectValue("phone", "forgot.business", e.getMessage());
            }
        }
        if (bindingResult.hasErrors()) {
            return REQUEST_VIEW;
        }
        ResetPasswordForm next = new ResetPasswordForm();
        next.setPhone(form.getPhone().trim());
        redirectAttributes.addFlashAttribute("form", next);
        redirectAttributes.addFlashAttribute("codeSent", true);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetForm(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ResetPasswordForm());
        }
        return RESET_VIEW;
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute("form") ResetPasswordForm form, BindingResult bindingResult,
                        HttpServletRequest request) {
        if (!bindingResult.hasErrors()) {
            try {
                passwordResetService.resetPassword(form.getPhone(), form.getCode(), form.getNewPassword(),
                        form.getConfirmPassword(), request.getRemoteAddr());
            } catch (BusinessException e) {
                Map<String, String> fieldErrors = e.getFieldErrors().isEmpty()
                        ? Map.of("code", e.getMessage()) : e.getFieldErrors();
                fieldErrors.forEach((field, message) -> bindingResult.rejectValue(field, "forgot.business", message));
            }
        }
        if (bindingResult.hasErrors()) {
            return RESET_VIEW;
        }
        return "redirect:/login?reset";
    }
}
