package vn.edu.fpt.contactbook.controller;

import org.springframework.validation.BindingResult;
import vn.edu.fpt.contactbook.exception.BusinessException;

/**
 * Đưa lỗi của BusinessException lên form: lỗi theo ô hiện cạnh ô, lỗi chung hiện đầu form.
 */
final class FormErrors {

    private FormErrors() {
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
