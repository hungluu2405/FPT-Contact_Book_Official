package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.GradeComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Công thức trung bình có trọng số, kiểm tra điểm, điều kiện tổng trọng số 100%.
 */
class GradeRuleTest {

    static final GradeComponent ASSIGNMENT = component(1L, "20", "10");
    static final GradeComponent MIDTERM = component(2L, "30", "10");
    static final GradeComponent FINAL = component(3L, "50", "10");

    @Test
    @DisplayName("Đủ điểm: Σ(điểm/max×10×trọng số)/100 = (8×20 + 7×30 + 9×50)/100 = 8,2")
    void averageWhenComplete() {
        assertThat(GradeRule.average(List.of(ASSIGNMENT, MIDTERM, FINAL),
                Map.of(1L, bd("8"), 2L, bd("7"), 3L, bd("9")))).isEqualByComparingTo("8.2");
    }

    @Test
    @DisplayName("Thiếu một đầu điểm → null (Chưa đủ điểm)")
    void averageWhenIncomplete() {
        assertThat(GradeRule.average(List.of(ASSIGNMENT, MIDTERM, FINAL), Map.of(1L, bd("8"), 2L, bd("7"))))
                .isNull();
    }

    @Test
    @DisplayName("Môn chưa có đầu điểm → null")
    void averageWithoutComponents() {
        assertThat(GradeRule.average(List.of(), Map.of())).isNull();
    }

    @Test
    @DisplayName("max_score < 10 được quy về thang 10: 4/5 với trọng số 100 → 8,0")
    void averageScalesMaxScore() {
        GradeComponent project = component(7L, "100", "5");

        assertThat(GradeRule.average(List.of(project), Map.of(7L, bd("4")))).isEqualByComparingTo("8.0");
    }

    @Test
    @DisplayName("Làm tròn HALF_UP 1 chữ số: 8,25 → 8,3 và 7,24 → 7,2")
    void averageRoundsHalfUp() {
        GradeComponent a = component(1L, "50", "10");
        GradeComponent b = component(2L, "50", "10");

        assertThat(GradeRule.average(List.of(a, b), Map.of(1L, bd("8"), 2L, bd("8.5")))).isEqualByComparingTo("8.3");
        assertThat(GradeRule.average(List.of(a, b), Map.of(1L, bd("7.23"), 2L, bd("7.25"))))
                .isEqualByComparingTo("7.2");
    }

    @Test
    @DisplayName("Trọng số lẻ và max_score lẻ: (2,5/4×10×33,33 + 6/7×10×66,67)/100 = 7,8")
    void averageWithOddWeights() {
        GradeComponent a = component(1L, "33.33", "4");
        GradeComponent b = component(2L, "66.67", "7");

        assertThat(GradeRule.average(List.of(a, b), Map.of(1L, bd("2.5"), 2L, bd("6"))))
                .isEqualByComparingTo("7.8");
    }

    @ParameterizedTest(name = "\"{0}\" với max {1} → {2}")
    @CsvSource(delimiter = '|', value = {
            "10    | 10 | OK",
            "0     | 10 | OK",
            "8.25  | 10 | OK",
            "10.01 | 10 | Điểm phải từ 0 đến 10",
            "-0.5  | 10 | Điểm phải từ 0 đến 10",
            "5.5   | 5  | Điểm phải từ 0 đến 5",
            "8.125 | 10 | Điểm tối đa 2 chữ số thập phân",
            "8.500 | 10 | OK"})
    @DisplayName("Điểm phải từ 0 đến max_score, tối đa 2 chữ số thập phân")
    void validate(String score, String max, String expected) {
        String error = GradeRule.validate(bd(score), bd(max));

        assertThat(error == null ? "OK" : error).isEqualTo(expected);
    }

    @Test
    @DisplayName("Đọc chữ: trống là null, nhận dấu phẩy, chữ cái bị từ chối")
    void parse() {
        assertThat(GradeRule.parse("  ")).isNull();
        assertThat(GradeRule.parse(" 8,5 ")).isEqualByComparingTo("8.5");
        assertThatThrownBy(() -> GradeRule.parse("tám")).isInstanceOf(BusinessException.class)
                .hasMessage(GradeMessages.SCORE_INVALID);
    }

    @Test
    @DisplayName("Tổng trọng số khác 100% hoặc môn chưa có đầu điểm thì bị chặn")
    void requireCompleteWeights() {
        GradeRule.requireCompleteWeights(List.of(ASSIGNMENT, MIDTERM, FINAL));

        assertThatThrownBy(() -> GradeRule.requireCompleteWeights(List.of(ASSIGNMENT, MIDTERM)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đang là 50%");
        assertThatThrownBy(() -> GradeRule.requireCompleteWeights(List.of()))
                .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.NO_COMPONENTS);
    }

    @Test
    @DisplayName("weightsComplete: đúng 100% là true; khác 100% hoặc môn chưa có đầu điểm là false")
    void weightsComplete() {
        assertThat(GradeRule.weightsComplete(List.of(ASSIGNMENT, MIDTERM, FINAL))).isTrue();
        assertThat(GradeRule.weightsComplete(List.of(ASSIGNMENT, MIDTERM))).isFalse();
        assertThat(GradeRule.weightsComplete(List.of())).isFalse();
    }

    static GradeComponent component(Long id, String weight, String maxScore) {
        GradeComponent component = new GradeComponent();
        component.setId(id);
        component.setName("Đầu điểm " + id);
        component.setWeight(bd(weight));
        component.setMaxScore(bd(maxScore));
        return component;
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
