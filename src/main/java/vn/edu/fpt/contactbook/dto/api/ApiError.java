package vn.edu.fpt.contactbook.dto.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dạng lỗi chung của REST API. fieldErrors rỗng nếu là lỗi chung; timestamp theo giờ Asia/Ho_Chi_Minh.
 */
public record ApiError(int status, String code, String message, List<FieldError> fieldErrors,
                       LocalDateTime timestamp) {

    public record FieldError(String field, String message) {
    }
}
