package vn.edu.fpt.contactbook.controller;

import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.exception.BusinessException;

/**
 * Đưa lỗi của BusinessException ra giao diện: lên form (lỗi theo ô hiện cạnh ô, lỗi chung đầu form)
 * hoặc qua flash cho thao tác từ nút bấm.
 */
final class FormErrors {

    private FormErrors() {
    }

    /**
     * Thao tác từ một nút bấm (khóa, gỡ, đặt chính...): thành công hay lỗi nghiệp vụ đều báo qua flash,
     * rồi chuyển về target.
     */
    static String runThenRedirect(String target, RedirectAttributes redirectAttributes, String success,
                                  Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + target;
    }

    static void apply(BindingResult bindingResult, BusinessException exception) {
        if (exception.getFieldErrors().isEmpty()) {
            bindingResult.reject("business", exception.getMessage());
            return;
        }
        exception.getFieldErrors().forEach((field, message) -> {
            if (bindingResult.getFieldErrorCount(field) == 0) {
                bindingResult.rejectValue(field, "business", message);
            }
        });
    }
}
