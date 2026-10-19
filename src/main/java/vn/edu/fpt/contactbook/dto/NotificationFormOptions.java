package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

import java.util.List;

/**
 * Lựa chọn trên form gửi thông báo, chỉ gồm những gì người gửi được phép: các phạm vi và danh sách đích của từng
 * phạm vi (vai trò, bộ môn, lớp hành chính, lớp học phần). Người nhận cá nhân nhập bằng số điện thoại.
 */
public record NotificationFormOptions(List<NotificationTargetType> types, List<OptionView> roles,
                                      List<OptionView> departments, List<OptionView> classes,
                                      List<OptionView> sections) {

    public boolean allows(String typeName) {
        return types.stream().anyMatch(type -> type.name().equals(typeName));
    }
}
