package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.fpt.contactbook.service.PasswordPolicy.Violation;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private static final String CURRENT = "Fpt@12345";

    @ParameterizedTest
    @ValueSource(strings = {"abcdef12", "MatKhau2026", "Mật khẩu 1", "a1234567"})
    @DisplayName("Mật khẩu hợp lệ")
    void acceptsValidPasswords(String password) {
        assertThat(PasswordPolicy.validate(CURRENT, password, password)).isEmpty();
    }

    @Test
    @DisplayName("Biên độ dài: 7 sai, 8 đúng, 64 đúng, 65 sai")
    void lengthBoundaries() {
        assertThat(validate("abcdef1")).containsExactly(Violation.TOO_SHORT);
        assertThat(validate("abcdefg1")).isEmpty();
        assertThat(validate("a".repeat(63) + "1")).isEmpty();
        assertThat(validate("a".repeat(64) + "1")).containsExactly(Violation.TOO_LONG);
    }

    @Test
    @DisplayName("Đếm ký tự theo code point, không theo đơn vị UTF-16")
    void countsCodePoints() {
        String emoji = "😀";
        assertThat(validate("a1" + emoji.repeat(3) + "b")).containsExactly(Violation.TOO_SHORT);
    }

    @Test
    @DisplayName("Quá 72 byte UTF-8 (giới hạn BCrypt) dù chưa quá 64 ký tự")
    void rejectsMoreThan72Bytes() {
        String vietnamese = "ệ".repeat(30) + "1";   // 31 ký tự, 91 byte
        assertThat(validate(vietnamese)).containsExactly(Violation.TOO_MANY_BYTES);
        assertThat(validate("ệ".repeat(23) + "1")).as("70 byte").isEmpty();
    }

    @Test
    @DisplayName("Thiếu chữ hoặc thiếu số")
    void requiresLetterAndDigit() {
        assertThat(validate("12345678")).containsExactly(Violation.MISSING_LETTER);
        assertThat(validate("abcdefgh")).containsExactly(Violation.MISSING_DIGIT);
        assertThat(validate("!@#$%^&*")).containsExactly(Violation.MISSING_LETTER, Violation.MISSING_DIGIT);
    }

    @Test
    @DisplayName("Trùng mật khẩu hiện tại")
    void rejectsSameAsCurrent() {
        assertThat(validate(CURRENT)).containsExactly(Violation.SAME_AS_CURRENT);
    }

    @Test
    @DisplayName("Nhập lại không khớp")
    void rejectsConfirmMismatch() {
        assertThat(PasswordPolicy.validate(CURRENT, "abcdefg1", "abcdefg2"))
                .containsExactly(Violation.CONFIRM_MISMATCH);
        assertThat(PasswordPolicy.validate(CURRENT, "abcdefg1", null))
                .containsExactly(Violation.CONFIRM_MISMATCH);
    }

    @Test
    @DisplayName("Mật khẩu mới null hoặc rỗng")
    void rejectsNullOrEmpty() {
        assertThat(PasswordPolicy.validate(CURRENT, null, null))
                .containsExactly(Violation.TOO_SHORT, Violation.MISSING_LETTER, Violation.MISSING_DIGIT);
        assertThat(PasswordPolicy.validate(null, "", ""))
                .containsExactly(Violation.TOO_SHORT, Violation.MISSING_LETTER, Violation.MISSING_DIGIT);
    }

    private static java.util.List<Violation> validate(String newPassword) {
        return PasswordPolicy.validate(CURRENT, newPassword, newPassword);
    }
}
