package vn.edu.fpt.contactbook.dto;

/**
 * Một người dùng vừa được nhập, kèm mật khẩu tạm chỉ để ghi vào file kết quả trả về ngay.
 */
public record ImportedUser(String phone, String fullName, String code, String temporaryPassword) {

    @Override
    public String toString() {
        return "ImportedUser[phone=" + phone + ", code=" + code + ", temporaryPassword=***]";
    }
}
