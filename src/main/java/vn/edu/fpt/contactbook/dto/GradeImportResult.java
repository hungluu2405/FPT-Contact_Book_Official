package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Kết quả nhập điểm từ Excel: errors rỗng là thành công; changed là số ô được tạo hoặc đổi điểm.
 */
public record GradeImportResult(List<ImportRowError> errors, int changed) {

    public static GradeImportResult failed(List<ImportRowError> errors) {
        return new GradeImportResult(List.copyOf(errors), 0);
    }

    public boolean success() {
        return errors.isEmpty();
    }
}
