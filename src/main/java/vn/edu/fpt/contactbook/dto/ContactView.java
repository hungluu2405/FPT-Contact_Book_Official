package vn.edu.fpt.contactbook.dto;

import java.util.List;

/**
 * Một người trong danh bạ "Nhắn tin mới"; roles là mã vai trò (nhãn lấy từ messages.properties).
 */
public record ContactView(Long userId, String fullName, List<String> roles) {
}
