package vn.edu.fpt.contactbook.service;

/**
 * Thông báo lỗi nghiệp vụ của lớp học phần và ghi danh (dùng chung cho kiểm tra trước và lỗi ràng buộc DB).
 */
final class SectionMessages {

    static final String SECTION_NOT_FOUND = "Không tìm thấy lớp học phần";
    static final String CODE_TAKEN = "Mã lớp học phần đã tồn tại";
    static final String SUBJECT_REQUIRED = "Vui lòng chọn môn học";
    static final String SEMESTER_REQUIRED = "Vui lòng chọn học kỳ";
    static final String TEACHER_REQUIRED = "Vui lòng chọn giảng viên";
    static final String TEACHER_OTHER_DEPARTMENT = "Giảng viên phải thuộc bộ môn của môn học";
    static final String MAX_STUDENTS_RANGE = "Sĩ số tối đa phải từ 1 đến 32767";
    static final String MAX_BELOW_ENROLLED = "Sĩ số tối đa không được nhỏ hơn số sinh viên đang học (%d)";
    static final String SUBJECT_LOCKED = "Lớp đã có sinh viên ghi danh nên không đổi được môn học";
    static final String SEMESTER_LOCKED = "Lớp đã có sinh viên ghi danh nên không đổi được học kỳ";
    static final String SUGGEST_NEEDS_SUBJECT_SEMESTER = "Chọn môn học và học kỳ trước khi đề xuất mã";
    static final String CONCURRENT_CHANGE = "Lớp học phần vừa được người khác thay đổi, vui lòng thử lại";

    static final String SECTION_NOT_OPEN = "Lớp học phần không ở trạng thái mở nên không ghi danh được";
    static final String STUDENT_NOT_FOUND = "Không tìm thấy sinh viên";
    static final String STUDENT_NOT_STUDYING = "Sinh viên không ở trạng thái đang học";
    static final String ALREADY_ENROLLED = "Sinh viên đã có trong lớp học phần";
    static final String ALREADY_COMPLETED = "Sinh viên đã hoàn thành lớp học phần này";
    static final String SECTION_FULL = "Lớp học phần đã đủ sĩ số (%d/%d)";
    static final String BATCH_OVER_CAPACITY = "Không đủ chỗ: cần thêm %d sinh viên nhưng lớp chỉ còn %d chỗ";
    static final String NOT_ENROLLED = "Sinh viên không đang học trong lớp học phần này";
    static final String CLASS_REQUIRED = "Vui lòng chọn lớp hành chính";

    private SectionMessages() {
    }
}
