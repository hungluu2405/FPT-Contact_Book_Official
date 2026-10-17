package vn.edu.fpt.contactbook.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Quy tắc mật khẩu mới: 8–64 ký tự, tối đa 72 byte UTF-8 (giới hạn của BCrypt), có chữ và số,
 * khác mật khẩu hiện tại, nhập lại khớp.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private PasswordPolicy() {
    }

    public enum Violation {
        TOO_SHORT("newPassword", "Mật khẩu mới phải có ít nhất 8 ký tự"),
        TOO_LONG("newPassword", "Mật khẩu mới tối đa 64 ký tự"),
        TOO_MANY_BYTES("newPassword", "Mật khẩu mới quá dài, hãy bớt ký tự có dấu"),
        MISSING_LETTER("newPassword", "Mật khẩu mới phải có ít nhất một chữ cái"),
        MISSING_DIGIT("newPassword", "Mật khẩu mới phải có ít nhất một chữ số"),
        SAME_AS_CURRENT("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại"),
        CONFIRM_MISMATCH("confirmPassword", "Mật khẩu nhập lại không khớp");

        private final String field;
        private final String message;

        Violation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String field() {
            return field;
        }

        public String message() {
            return message;
        }
    }

    public static List<Violation> validate(String currentPassword, String newPassword, String confirmPassword) {
        List<Violation> violations = new ArrayList<>();
        String candidate = newPassword == null ? "" : newPassword;
        int length = candidate.codePointCount(0, candidate.length());
        if (length < MIN_LENGTH) {
            violations.add(Violation.TOO_SHORT);
        } else if (length > MAX_LENGTH) {
            violations.add(Violation.TOO_LONG);
        } else if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add(Violation.TOO_MANY_BYTES);
        }
        if (!LETTER.matcher(candidate).find()) {
            violations.add(Violation.MISSING_LETTER);
        }
        if (!DIGIT.matcher(candidate).find()) {
            violations.add(Violation.MISSING_DIGIT);
        }
        if (!candidate.isEmpty() && candidate.equals(currentPassword)) {
            violations.add(Violation.SAME_AS_CURRENT);
        }
        if (!candidate.equals(confirmPassword == null ? "" : confirmPassword)) {
            violations.add(Violation.CONFIRM_MISMATCH);
        }
        return violations;
    }
}
