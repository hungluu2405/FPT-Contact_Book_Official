package vn.edu.fpt.contactbook.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "0900000001, 0900000001",
            "+84900000001, 0900000001",
            "84900000001, 0900000001",
            "'+84 900 000 001', 0900000001",
            "'090 000 0001', 0900000001",
            "0900.000.001, 0900000001",
            "0900-000-001, 0900000001",
            "' 0900000001 ', 0900000001",
            "'+84-90.000 0001', 0900000001",
            "'0900\t000\t001', 0900000001"
    })
    void normalizesSupportedFormats(String raw, String expected) {
        assertThat(PhoneUtils.normalize(raw)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "090000000",          // 9 chữ số
            "09000000011",        // 11 chữ số
            "1900000001",         // không bắt đầu bằng 0
            "09000000ab",         // có chữ
            "(090)0000001",       // ngoặc không phải ký tự được bỏ
            "+840900000001",      // +84 kèm số 0 thừa
            "8490000000",         // 84 nhưng thiếu chữ số
            "+1 900000001",
            "０９００００００01"  // chữ số toàn chiều rộng
    })
    void rejectsInvalidNumbers(String raw) {
        assertThat(PhoneUtils.normalize(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0900000001", "0123456789"})
    void validAcceptsStoredFormat(String phone) {
        assertThat(PhoneUtils.isValid(phone)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"+84900000001", "0900 000 001", "900000001"})
    void validRejectsUnnormalizedOrInvalid(String phone) {
        assertThat(PhoneUtils.isValid(phone)).isFalse();
    }
}
