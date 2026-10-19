package vn.edu.fpt.contactbook.dto;

import java.time.LocalDateTime;

/**
 * Một dòng nhật ký hệ thống; userId null là thao tác không gắn với tài khoản (ví dụ đăng nhập bằng số không tồn tại).
 */
public record AuditLogView(Long id, LocalDateTime createdAt, Long userId, String userName, String userPhone,
                           String action, String entityType, Long entityId, String detail, String ipAddress) {
}
