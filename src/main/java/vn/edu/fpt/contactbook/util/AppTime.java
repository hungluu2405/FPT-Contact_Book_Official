package vn.edu.fpt.contactbook.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Múi giờ nghiệp vụ của hệ thống (CLAUDE.md mục 6), không phụ thuộc múi giờ mặc định của JVM.
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
