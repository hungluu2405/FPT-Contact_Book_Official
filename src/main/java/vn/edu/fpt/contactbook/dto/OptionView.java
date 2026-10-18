package vn.edu.fpt.contactbook.dto;

/**
 * Một lựa chọn trong ô chọn; groupId là nhóm cha để lọc: ngành của lớp, bộ môn của môn học và của giảng viên.
 */
public record OptionView(Long id, String code, String name, Long groupId) {
}
