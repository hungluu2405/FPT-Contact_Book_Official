package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.GradeComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Quy tắc điểm số (SRS FR-GRADE-02, 05), dùng chung cho mọi màn hình:
 * điểm hợp lệ là 0 ≤ điểm ≤ max_score của đầu điểm, tối đa 2 chữ số thập phân;
 * trung bình môn = Σ(điểm / max_score × 10 × trọng số) / 100, làm tròn HALF_UP 1 chữ số thập phân,
 * chỉ tính khi MỌI đầu điểm đều có điểm (thiếu → null, hiển thị "Chưa đủ điểm").
 */
final class GradeRule {

    private static final int SCALE = 2;
    private static final BigDecimal TEN = BigDecimal.TEN;

    private GradeRule() {
    }

    /** Chữ nhập trên form: trống → null; nhận cả "8.5" lẫn "8,5". */
    static BigDecimal parse(String raw) {
        String value = CatalogSupport.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new BusinessException(GradeMessages.SCORE_INVALID);
        }
    }

    /** Lỗi của một điểm đã đọc được, hoặc null nếu hợp lệ; điểm null (ô trống) luôn hợp lệ. */
    static String validate(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return null;
        }
        if (score.signum() < 0 || score.compareTo(maxScore) > 0) {
            return GradeMessages.SCORE_RANGE.formatted(maxScore.stripTrailingZeros().toPlainString());
        }
        if (score.stripTrailingZeros().scale() > SCALE) {
            return GradeMessages.SCORE_SCALE;
        }
        return null;
    }

    /** Điểm lưu xuống DECIMAL(4,2). */
    static BigDecimal normalize(BigDecimal score) {
        return score == null ? null : score.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    static boolean sameScore(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /**
     * Trung bình môn trên thang 10. scores: componentId → điểm (thiếu khóa hoặc null là chưa có điểm).
     * null nếu môn chưa có đầu điểm hoặc còn đầu điểm chưa có điểm.
     */
    static BigDecimal average(List<GradeComponent> components, Map<Long, BigDecimal> scores) {
        if (components.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GradeComponent component : components) {
            BigDecimal score = scores.get(component.getId());
            if (score == null) {
                return null;
            }
            sum = sum.add(score.multiply(TEN).multiply(component.getWeight())
                    .divide(component.getMaxScore(), 10, RoundingMode.HALF_UP));
        }
        return sum.divide(GradeComponentService.HUNDRED, 1, RoundingMode.HALF_UP);
    }

    /** Tổng trọng số đúng 100 và môn có ít nhất một đầu điểm (như requireCompleteWeights, nhưng không ném lỗi). */
    static boolean weightsComplete(List<GradeComponent> components) {
        return !components.isEmpty() && components.stream().map(GradeComponent::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(GradeComponentService.HUNDRED) == 0;
    }

    /** Tổng trọng số phải đúng 100 (giả định 35); môn chưa có đầu điểm cũng bị chặn. */
    static void requireCompleteWeights(List<GradeComponent> components) {
        if (components.isEmpty()) {
            throw new BusinessException(GradeMessages.NO_COMPONENTS);
        }
        BigDecimal total = components.stream().map(GradeComponent::getWeight).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        if (total.compareTo(GradeComponentService.HUNDRED) != 0) {
            throw new BusinessException(GradeMessages.WEIGHTS_INCOMPLETE.formatted(
                    total.stripTrailingZeros().toPlainString()));
        }
    }
}
