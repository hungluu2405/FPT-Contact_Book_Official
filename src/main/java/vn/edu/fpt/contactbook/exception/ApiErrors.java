package vn.edu.fpt.contactbook.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.api.ApiError;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.List;

/**
 * Dựng ApiError với thông báo tiếng Việt từ messages.properties; dùng chung cho ApiExceptionHandler và các
 * handler của chuỗi bảo mật API (lỗi xảy ra trước khi tới controller).
 */
@Component
@RequiredArgsConstructor
public class ApiErrors {

    private final MessageSource messageSource;

    public ApiError of(ApiErrorCode code) {
        return of(code, messageSource.getMessage(code.getMessageKey(), null, LocaleContextHolder.getLocale()),
                List.of());
    }

    public ApiError of(ApiErrorCode code, List<ApiError.FieldError> fieldErrors) {
        return of(code, messageSource.getMessage(code.getMessageKey(), null, LocaleContextHolder.getLocale()),
                fieldErrors);
    }

    public ApiError of(ApiErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        return new ApiError(code.getStatus().value(), code.name(), message, List.copyOf(fieldErrors), AppTime.now());
    }
}
