package vn.edu.fpt.contactbook.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lỗi nghiệp vụ, thông báo tiếng Việt hiển thị được cho người dùng.
 * fieldErrors: ô trên form → thông báo (rỗng nếu là lỗi chung); field là ô đầu tiên.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public BusinessException(String message) {
        this(message, null);
    }

    public BusinessException(String message, String field) {
        super(message);
        this.fieldErrors = field == null ? Map.of() : Map.of(field, message);
    }

    /** Nhiều lỗi theo ô cùng lúc; thông báo chung là lỗi đầu tiên. */
    public BusinessException(Map<String, String> fieldErrors) {
        super(fieldErrors.values().iterator().next());
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public String getField() {
        return fieldErrors.isEmpty() ? null : fieldErrors.keySet().iterator().next();
    }
}
