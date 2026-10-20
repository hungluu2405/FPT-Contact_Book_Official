package vn.edu.fpt.contactbook.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
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

    /** Thông báo của một ô: mã typeMismatch.* trong messages.properties, nếu không có thì thông báo của ràng buộc. */
    public String message(FieldError error) {
        return messageSource.getMessage(error, LocaleContextHolder.getLocale());
    }

    /** Thông báo sai kiểu theo kiểu đích (typeMismatch.java.time.LocalDate...), không có thì typeMismatch chung. */
    public String typeMismatch(Class<?> requiredType) {
        String[] codes = requiredType == null ? new String[]{"typeMismatch"}
                : new String[]{"typeMismatch." + requiredType.getName(), "typeMismatch"};
        return messageSource.getMessage(new DefaultMessageSourceResolvable(codes, null, null),
                LocaleContextHolder.getLocale());
    }

    public ApiError of(ApiErrorCode code, String message, List<ApiError.FieldError> fieldErrors) {
        return new ApiError(code.getStatus().value(), code.name(), message, List.copyOf(fieldErrors), AppTime.now());
    }
}
