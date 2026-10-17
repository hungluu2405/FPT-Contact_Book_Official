package vn.edu.fpt.contactbook.dto;

/**
 * Kết quả tạo người dùng hoặc đặt lại mật khẩu: mật khẩu tạm chỉ đi tới tầng web để hiện một lần.
 */
public record CreatedUser(Long userId, String temporaryPassword) {

    @Override
    public String toString() {
        return "CreatedUser[userId=" + userId + ", temporaryPassword=***]";
    }
}
