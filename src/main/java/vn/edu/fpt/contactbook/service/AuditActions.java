package vn.edu.fpt.contactbook.service;

/**
 * Mã hành động và loại đối tượng ghi vào audit_logs, trùng tên với dữ liệu mẫu trong script.
 */
public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String UPDATE_PROFILE = "UPDATE_PROFILE";

    public static final String ENTITY_USER = "User";

    private AuditActions() {
    }
}
