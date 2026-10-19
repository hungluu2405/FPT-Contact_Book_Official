package vn.edu.fpt.contactbook.dto;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

/**
 * Báo cáo điểm (SRS FR-REPORT-02), tính trên mọi điểm đã nhập (kể cả chưa công bố) của sinh viên ENROLLED, trung
 * bình môn bằng GradeRule.average như bảng điểm. Chế độ:
 * SECTION: một lớp (section, stats của lớp); SUBJECT: một môn (stats gộp các lớp của môn, rows từng lớp);
 * OVERVIEW: mọi lớp trong phạm vi của học kỳ (rows, stats null).
 */
public record GradeReport(ReportSelection selection, Mode mode, SectionDetail section, GradeStats stats,
                          Page<SectionRow> rows) {

    public enum Mode { SECTION, SUBJECT, OVERVIEW }

    /** Bảng điểm của một lớp: điểm từng đầu điểm theo thứ tự components; average null là "Chưa đủ điểm". */
    public record SectionDetail(TeacherSectionView section, String teacherName,
                                List<GradeSheet.Component> components, boolean weightsComplete,
                                List<StudentRow> students) {
    }

    public record StudentRow(String studentCode, String fullName, List<BigDecimal> scores, BigDecimal average) {
    }

    /** Thống kê của một lớp; weightsComplete = tổng trọng số đầu điểm của môn đúng 100%. */
    public record SectionRow(TeacherSectionView section, String teacherName, boolean weightsComplete,
                             GradeStats stats) {
    }
}
