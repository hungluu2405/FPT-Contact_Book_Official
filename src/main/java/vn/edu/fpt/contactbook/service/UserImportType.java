package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.enums.RoleCodes;

import java.util.List;
import java.util.Optional;

import static vn.edu.fpt.contactbook.service.ImportColumn.ACADEMIC_TITLE;
import static vn.edu.fpt.contactbook.service.ImportColumn.ADDRESS;
import static vn.edu.fpt.contactbook.service.ImportColumn.CLASS_CODE;
import static vn.edu.fpt.contactbook.service.ImportColumn.DATE_OF_BIRTH;
import static vn.edu.fpt.contactbook.service.ImportColumn.DEPARTMENT_CODE;
import static vn.edu.fpt.contactbook.service.ImportColumn.EMAIL;
import static vn.edu.fpt.contactbook.service.ImportColumn.ENROLLMENT_YEAR;
import static vn.edu.fpt.contactbook.service.ImportColumn.FULL_NAME;
import static vn.edu.fpt.contactbook.service.ImportColumn.GENDER;
import static vn.edu.fpt.contactbook.service.ImportColumn.MAJOR_CODE;
import static vn.edu.fpt.contactbook.service.ImportColumn.PHONE;
import static vn.edu.fpt.contactbook.service.ImportColumn.STUDENT_CODE;
import static vn.edu.fpt.contactbook.service.ImportColumn.STUDENT_STATUS;
import static vn.edu.fpt.contactbook.service.ImportColumn.TEACHER_CODE;

/**
 * Loại file nhập: sinh viên (vai trò STUDENT) hoặc giảng viên (chỉ vai trò TEACHER;
 * HEAD_OF_DEPARTMENT gán tay ở màn hình vai trò). Thứ tự cột là thứ tự trong file mẫu.
 */
public enum UserImportType {

    STUDENT("Học sinh", "sinh-vien", RoleCodes.STUDENT, List.of(PHONE, FULL_NAME, GENDER, DATE_OF_BIRTH, EMAIL,
            ADDRESS, STUDENT_CODE, MAJOR_CODE, CLASS_CODE, ENROLLMENT_YEAR, STUDENT_STATUS)),
    TEACHER("Giáo viên", "giang-vien", RoleCodes.TEACHER, List.of(PHONE, FULL_NAME, GENDER, DATE_OF_BIRTH, EMAIL,
            ADDRESS, TEACHER_CODE, DEPARTMENT_CODE, ACADEMIC_TITLE));

    private final String label;
    private final String slug;
    private final String roleCode;
    private final List<ImportColumn> columns;

    UserImportType(String label, String slug, String roleCode, List<ImportColumn> columns) {
        this.label = label;
        this.slug = slug;
        this.roleCode = roleCode;
        this.columns = columns;
    }

    public String label() {
        return label;
    }

    /** Phần tên file không dấu, ví dụ mau-nhap-sinh-vien.xlsx. */
    public String slug() {
        return slug;
    }

    public String roleCode() {
        return roleCode;
    }

    public List<ImportColumn> columns() {
        return columns;
    }

    /** Cột chứa ô của UserForm; rỗng nếu lỗi không gắn với cột nào. */
    public Optional<ImportColumn> columnOf(String field) {
        return columns.stream().filter(column -> column.field().equals(field)).findFirst();
    }
}
