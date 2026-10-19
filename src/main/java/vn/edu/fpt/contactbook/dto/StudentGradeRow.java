package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điểm ĐÃ CÔNG BỐ của một sinh viên trong một lớp học phần (trang điểm của sinh viên và phụ huynh).
 * Ô chưa công bố có score null giống ô chưa có điểm; average null là "Chưa đủ điểm".
 */
public record StudentGradeRow(String sectionCode, String subjectCode, String subjectName, List<Cell> cells,
                              BigDecimal average) {

    public record Cell(String componentName, BigDecimal weight, BigDecimal maxScore, BigDecimal score) {
    }
}
