package vn.edu.fpt.contactbook.service;

/**
 * Mã hành động và loại đối tượng ghi vào audit_logs, trùng tên với dữ liệu mẫu trong script.
 */
public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
    public static final String UPDATE_PROFILE = "UPDATE_PROFILE";

    public static final String CREATE_USER = "CREATE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String ASSIGN_ROLE = "ASSIGN_ROLE";
    public static final String REMOVE_ROLE = "REMOVE_ROLE";
    public static final String LOCK_USER = "LOCK_USER";
    public static final String UNLOCK_USER = "UNLOCK_USER";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";
    public static final String LINK_PARENT = "LINK_PARENT";
    public static final String UNLINK_PARENT = "UNLINK_PARENT";
    public static final String IMPORT_USERS = "IMPORT_USERS";

    public static final String ENTITY_USER = "User";

    private AuditActions() {
    }
}
