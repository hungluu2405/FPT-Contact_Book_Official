package vn.edu.fpt.contactbook.dto;

/**
 * Một lựa chọn trong ô chọn; groupId dùng cho lớp (ngành của lớp).
 */
public record OptionView(Long id, String code, String name, Long groupId) {
}
