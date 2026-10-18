package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Kết quả nhập: có lỗi thì không người dùng nào được tạo (created rỗng); không lỗi thì errors rỗng.
 */
public record UserImportResult(List<ImportRowError> errors, List<ImportedUser> created) {

    public static UserImportResult failed(List<ImportRowError> errors) {
        return new UserImportResult(List.copyOf(errors), List.of());
    }

    public static UserImportResult succeeded(List<ImportedUser> created) {
        return new UserImportResult(List.of(), List.copyOf(created));
    }

    public boolean success() {
        return errors.isEmpty();
    }
}
