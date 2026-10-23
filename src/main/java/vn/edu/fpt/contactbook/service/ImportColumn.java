package vn.edu.fpt.contactbook.service;

/**
 * Cột của file nhập người dùng: tiêu đề tiếng Việt, ô tương ứng trong UserForm (để đổi lỗi của bộ kiểm tra
 * dùng chung thành tên cột), và có phải cột định dạng Text trong file mẫu hay không.
 */
public enum ImportColumn {

    PHONE("Số điện thoại", "phone", true),
    FULL_NAME("Họ tên", "fullName", false),
    GENDER("Giới tính", "gender", false),
    DATE_OF_BIRTH("Ngày sinh", "dateOfBirth", false),
    EMAIL("Email", "email", false),
    ADDRESS("Địa chỉ", "address", false),
    STUDENT_CODE("Mã HS", "profile.studentCode", true),
    MAJOR_CODE("Mã khối", "profile.majorId", true),
    CLASS_CODE("Mã lớp", "profile.classId", true),
    ENROLLMENT_YEAR("Năm nhập học", "profile.enrollmentYear", false),
    STUDENT_STATUS("Trạng thái", "profile.studentStatus", false),
    TEACHER_CODE("Mã GV", "profile.teacherCode", true),
    DEPARTMENT_CODE("Mã tổ chuyên môn", "profile.teacherDepartmentId", true),
    ACADEMIC_TITLE("Học hàm học vị", "profile.academicTitle", false);

    private final String header;
    private final String field;
    private final boolean text;

    ImportColumn(String header, String field, boolean text) {
        this.header = header;
        this.field = field;
        this.text = text;
    }

    public String header() {
        return header;
    }

    public String field() {
        return field;
    }

    /** Cột định dạng Text trong file mẫu (số điện thoại, các mã) để Excel không bỏ số 0 đầu. */
    public boolean text() {
        return text;
    }
}
