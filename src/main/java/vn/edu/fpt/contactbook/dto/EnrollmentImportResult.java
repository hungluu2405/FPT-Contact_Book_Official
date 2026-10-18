package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Kết quả nhập ghi danh từ Excel: có lỗi thì errors không rỗng và không dòng nào được ghi.
 */
public record EnrollmentImportResult(List<ImportRowError> errors, int added, int reEnrolled) {

    public static EnrollmentImportResult failed(List<ImportRowError> errors) {
        return new EnrollmentImportResult(List.copyOf(errors), 0, 0);
    }

    public boolean success() {
        return errors.isEmpty();
    }
}
