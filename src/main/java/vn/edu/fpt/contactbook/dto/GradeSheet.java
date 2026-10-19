package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng điểm một lớp học phần cho giảng viên và người quản lý: sinh viên đang học × đầu điểm của môn (cả ô chưa
 * công bố). average null là "Chưa đủ điểm". weightsComplete = tổng trọng số đúng 100% (mới nhập, công bố được);
 * publishable = số ô đã có điểm mà chưa công bố.
 */
public record GradeSheet(TeacherSectionView section, List<Component> components, BigDecimal totalWeight,
                         boolean weightsComplete, List<Row> rows, long publishable) {

    public record Component(Long id, String name, BigDecimal weight, BigDecimal maxScore) {
    }

    public record Row(Long enrollmentId, String studentCode, String fullName, List<Cell> cells,
                      BigDecimal average) {
    }

    /** gradeId null là ô chưa có dòng trong bảng grades. */
    public record Cell(Long gradeId, Long enrollmentId, Long componentId, BigDecimal score, boolean published,
                       String note) {

        /** Tên ô trên form nhập điểm: scores[key]. */
        public String key() {
            return GradeSheet.key(enrollmentId, componentId);
        }
    }

    public static String key(Long enrollmentId, Long componentId) {
        return enrollmentId + "_" + componentId;
    }
}
