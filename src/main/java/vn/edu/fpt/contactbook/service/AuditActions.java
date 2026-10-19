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

    public static final String CREATE_FACULTY = "CREATE_FACULTY";
    public static final String UPDATE_FACULTY = "UPDATE_FACULTY";
    public static final String CREATE_DEPARTMENT = "CREATE_DEPARTMENT";
    public static final String UPDATE_DEPARTMENT = "UPDATE_DEPARTMENT";
    public static final String CREATE_MAJOR = "CREATE_MAJOR";
    public static final String UPDATE_MAJOR = "UPDATE_MAJOR";
    public static final String CREATE_CLASS = "CREATE_CLASS";
    public static final String UPDATE_CLASS = "UPDATE_CLASS";
    public static final String CREATE_SUBJECT = "CREATE_SUBJECT";
    public static final String UPDATE_SUBJECT = "UPDATE_SUBJECT";
    public static final String CREATE_GRADE_COMPONENT = "CREATE_GRADE_COMPONENT";
    public static final String UPDATE_GRADE_COMPONENT = "UPDATE_GRADE_COMPONENT";
    public static final String DELETE_GRADE_COMPONENT = "DELETE_GRADE_COMPONENT";
    public static final String CREATE_SEMESTER = "CREATE_SEMESTER";
    public static final String UPDATE_SEMESTER = "UPDATE_SEMESTER";
    public static final String SET_CURRENT_SEMESTER = "SET_CURRENT_SEMESTER";
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String UPDATE_ROOM = "UPDATE_ROOM";

    public static final String CREATE_CLASS_SECTION = "CREATE_CLASS_SECTION";
    public static final String UPDATE_CLASS_SECTION = "UPDATE_CLASS_SECTION";
    public static final String ENROLL_STUDENT = "ENROLL_STUDENT";
    public static final String DROP_STUDENT = "DROP_STUDENT";
    public static final String ENROLL_CLASS = "ENROLL_CLASS";
    public static final String IMPORT_ENROLLMENTS = "IMPORT_ENROLLMENTS";

    public static final String CREATE_SESSION = "CREATE_SESSION";
    public static final String UPDATE_SESSION = "UPDATE_SESSION";
    public static final String CANCEL_SESSION = "CANCEL_SESSION";
    public static final String CREATE_WEEKLY_SESSIONS = "CREATE_WEEKLY_SESSIONS";
    public static final String CREATE_EXAM = "CREATE_EXAM";
    public static final String UPDATE_EXAM = "UPDATE_EXAM";
    public static final String DELETE_EXAM = "DELETE_EXAM";

    public static final String MARK_ATTENDANCE = "MARK_ATTENDANCE";
    public static final String ADJUST_ATTENDANCE = "ADJUST_ATTENDANCE";

    public static final String ENTER_GRADES = "ENTER_GRADES";
    public static final String IMPORT_GRADES = "IMPORT_GRADES";
    public static final String PUBLISH_GRADE = "PUBLISH_GRADE";
    public static final String ADJUST_GRADE = "ADJUST_GRADE";

    public static final String ENTITY_USER = "User";
    public static final String ENTITY_FACULTY = "Faculty";
    public static final String ENTITY_DEPARTMENT = "Department";
    public static final String ENTITY_MAJOR = "Major";
    public static final String ENTITY_CLASS = "SchoolClass";
    public static final String ENTITY_SUBJECT = "Subject";
    public static final String ENTITY_GRADE_COMPONENT = "GradeComponent";
    public static final String ENTITY_SEMESTER = "Semester";
    public static final String ENTITY_ROOM = "Room";
    public static final String ENTITY_CLASS_SECTION = "ClassSection";
    public static final String ENTITY_CLASS_SESSION = "ClassSession";
    public static final String ENTITY_EXAM = "Exam";
    public static final String ENTITY_ATTENDANCE = "Attendance";
    public static final String ENTITY_GRADE = "Grade";

    private AuditActions() {
    }
}
