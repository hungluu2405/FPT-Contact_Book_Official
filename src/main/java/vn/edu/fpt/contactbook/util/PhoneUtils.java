package vn.edu.fpt.contactbook.util;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa số điện thoại về dạng lưu trong users.phone: 10 chữ số, bắt đầu bằng 0
 * (khớp CHECK ck_users_phone).
 */
public final class PhoneUtils {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");
    private static final Pattern VALID = Pattern.compile("0\\d{9}");

    private PhoneUtils() {
    }

    /**
     * Bỏ khoảng trắng, dấu chấm, gạch ngang; đổi +84 / 84 ở đầu thành 0.
     * Trả về rỗng nếu kết quả không phải số hợp lệ.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String phone = SEPARATORS.matcher(raw).replaceAll("");
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        } else if (phone.startsWith("84") && phone.length() == 11) {
            phone = "0" + phone.substring(2);
        }
        return isValid(phone) ? Optional.of(phone) : Optional.empty();
    }

    public static boolean isValid(String phone) {
        return phone != null && VALID.matcher(phone).matches();
    }
}
