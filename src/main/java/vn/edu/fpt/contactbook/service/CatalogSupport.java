package vn.edu.fpt.contactbook.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.util.Locale;
import java.util.Map;

/**
 * Phần dùng chung của các service danh mục: chuẩn hóa mã, phân trang, ném lỗi theo ô.
 */
final class CatalogSupport {

    static final int PAGE_SIZE = 20;

    private CatalogSupport() {
    }

    /** Mã danh mục: bỏ khoảng trắng hai đầu, viết hoa; rỗng thành null. */
    static String normalizeCode(String code) {
        String value = trimToNull(code);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static Pageable page(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }

    static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessException(errors);
        }
    }

    /** Kiểm tra bắt buộc và độ dài ở service (form đã có Bean Validation, đây là chốt cho mọi đường gọi). */
    static String requireText(String raw, int maxLength, String field, String requiredMessage,
                              String sizeMessage, Map<String, String> errors) {
        String value = trimToNull(raw);
        if (value == null) {
            errors.putIfAbsent(field, requiredMessage);
        } else if (value.length() > maxLength) {
            errors.putIfAbsent(field, sizeMessage);
        }
        return value;
    }

    static String optionalText(String raw, int maxLength, String field, String sizeMessage,
                               Map<String, String> errors) {
        String value = trimToNull(raw);
        if (value != null && value.length() > maxLength) {
            errors.putIfAbsent(field, sizeMessage);
        }
        return value;
    }

    /** Mã đã chuẩn hóa; mọi mã danh mục trong script là nvarchar(20). */
    static String requireCode(String raw, Map<String, String> errors) {
        String code = normalizeCode(raw);
        if (code == null) {
            errors.putIfAbsent("code", "Vui lòng nhập mã");
        } else if (code.length() > 20) {
            errors.putIfAbsent("code", "Mã tối đa 20 ký tự");
        }
        return code;
    }
}
