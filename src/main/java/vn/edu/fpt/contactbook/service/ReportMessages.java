package vn.edu.fpt.contactbook.service;

import java.util.List;

/**
 * Chữ tiếng Việt trong file báo cáo xuất ra và thông báo lỗi của nhật ký hệ thống.
 */
final class ReportMessages {

    static final String ATTENDANCE_TITLE = "Báo cáo điểm danh";
    static final String GRADE_TITLE = "Báo cáo điểm";
    static final String SEMESTER = "Học kỳ: ";
    static final String SUBJECT = "Môn học: ";
    static final String SECTION = "Lớp học phần: ";
    static final String TEACHER = "Giảng viên: ";
    static final String THRESHOLD = "Ngưỡng cảnh báo vắng: trên %d%% số buổi";
    static final String EXPORTED_AT = "Xuất lúc: ";
    static final String ALL = "Tất cả";
    static final String YES = "Có";
    static final String INCOMPLETE = "Chưa đủ điểm";
    static final String WEIGHTS_INCOMPLETE = "Tổng trọng số khác 100%";
    static final String TOTAL = "Tổng";
    static final String WHOLE_SUBJECT = "Cả môn";

    static final String SHEET_STUDENTS = "Sinh viên";
    static final String SHEET_SECTIONS = "Lớp học phần";
    static final String SHEET_STATS = "Thống kê";

    static final List<String> ATTENDANCE_STUDENT_HEADERS = List.of("Mã SV", "Họ tên", "Có mặt", "Đi muộn",
            "Vắng có phép", "Vắng không phép", "Số buổi đã điểm danh", "Số buổi của lớp", "Chuyên cần (%)",
            "Tỷ lệ vắng (%)", "Vượt ngưỡng");
    static final List<String> ATTENDANCE_SECTION_HEADERS = List.of("Lớp học phần", "Mã môn", "Tên môn",
            "Giảng viên", "Số SV", "Số buổi", "Chuyên cần (%)", "Số SV vượt ngưỡng");
    static final List<String> GRADE_SECTION_HEADERS = List.of("Lớp học phần", "Mã môn", "Tên môn", "Giảng viên",
            "Số SV", "Đủ điểm", "Chưa đủ điểm", "Điểm trung bình", "Số SV đạt", "Tỷ lệ đạt (%)");
    static final List<String> GRADE_STATS_HEADERS = List.of("Chỉ số", "Giá trị");
    static final String STATS_STUDENTS = "Số SV";
    static final String STATS_COMPLETE = "Đủ điểm";
    static final String STATS_INCOMPLETE = "Chưa đủ điểm";
    static final String STATS_MEAN = "Điểm trung bình";
    static final String STATS_PASSED = "Số SV đạt (≥ 5,0)";
    static final String STATS_PASS_RATE = "Tỷ lệ đạt (%)";
    static final String STATS_BAND = "Số SV khoảng ";
    static final String STUDENT_CODE = "Mã SV";
    static final String FULL_NAME = "Họ tên";
    static final String AVERAGE = "Trung bình môn";

    static final String AUDIT_RANGE_INVALID = "Đến ngày phải bằng hoặc sau từ ngày";
    static final String AUDIT_NOT_FOUND = "Không tìm thấy dòng nhật ký";

    private ReportMessages() {
    }
}
