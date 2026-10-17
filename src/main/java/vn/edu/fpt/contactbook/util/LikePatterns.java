package vn.edu.fpt.contactbook.util;

import java.util.Locale;

/**
 * Mẫu LIKE "chứa chuỗi" an toàn cho SQL Server: escape %, _, [ bằng '\' (dùng với ESCAPE '\'),
 * để người dùng gõ "_" hay "50%" không thành ký tự đại diện.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** null nếu từ khóa rỗng; ngược lại "%từ khóa đã escape, chữ thường%". */
    public static String containsIgnoreCase(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
        return "%" + escaped + "%";
    }
}
