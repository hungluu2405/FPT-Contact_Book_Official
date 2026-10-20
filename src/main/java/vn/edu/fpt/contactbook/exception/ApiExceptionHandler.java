package vn.edu.fpt.contactbook.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.fpt.contactbook.dto.api.ApiError;

import java.util.List;

/**
 * Lỗi của controller REST (package controller.api) → JSON ApiError, xét trước GlobalExceptionHandler của web.
 * Không lộ câu SQL hay stack trace; lỗi lạ ghi log và trả INTERNAL_ERROR.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "vn.edu.fpt.contactbook.controller.api")
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final ApiErrors apiErrors;

    /** Body JSON (@RequestBody) hoặc form multipart (@ModelAttribute) không hợp lệ; sai kiểu dùng typeMismatch.*. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> invalid(BindException e) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), apiErrors.message(error)))
                .toList();
        return respond(apiErrors.of(ApiErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    /** Tham số trên URL sai kiểu (ngày, số...): lỗi gắn với tên tham số, thông báo typeMismatch.* như form web. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(apiErrors.of(ApiErrorCode.VALIDATION_FAILED,
                List.of(new ApiError.FieldError(e.getName(), apiErrors.typeMismatch(e.getRequiredType())))));
    }

    /** Body không phải JSON hoặc sai kiểu dữ liệu. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return respond(apiErrors.of(ApiErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
        return respond(apiErrors.of(ApiErrorCode.BAD_CREDENTIALS));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> locked(LockedException e) {
        return respond(apiErrors.of(ApiErrorCode.ACCOUNT_LOCKED_TEMPORARILY));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> disabled(DisabledException e) {
        return respond(apiErrors.of(ApiErrorCode.ACCOUNT_DISABLED));
    }

    @ExceptionHandler(ApiAuthException.class)
    public ResponseEntity<ApiError> apiAuth(ApiAuthException e) {
        return respond(apiErrors.of(e.getReason() == ApiAuthException.Reason.NO_ROLE
                ? ApiErrorCode.NO_ROLE : ApiErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException e) {
        return respond(apiErrors.of(ApiErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        List<ApiError.FieldError> fieldErrors = e.getFieldErrors().entrySet().stream()
                .map(entry -> new ApiError.FieldError(entry.getKey(), entry.getValue()))
                .toList();
        return respond(apiErrors.of(ApiErrorCode.BUSINESS_ERROR, e.getMessage(), fieldErrors));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return respond(apiErrors.of(ApiErrorCode.NOT_FOUND));
    }

    /** Vi phạm ràng buộc database (UNIQUE, CHECK...) lọt qua kiểm tra ở service. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return respond(apiErrors.of(ApiErrorCode.CONFLICT));
    }

    /** Lỗi 4xx do Spring MVC ném (thiếu tham số, sai kiểu tham số...): báo lỗi dữ liệu thay vì 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            return respond(apiErrors.of(response.getStatusCode().value() == 404
                    ? ApiErrorCode.NOT_FOUND : ApiErrorCode.VALIDATION_FAILED));
        }
        log.error("Unexpected API error", e);
        return respond(apiErrors.of(ApiErrorCode.INTERNAL_ERROR));
    }

    private static ResponseEntity<ApiError> respond(ApiError error) {
        return ResponseEntity.status(error.status()).body(error);
    }
}
