package vn.edu.fpt.contactbook.service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Mã hành động và loại đối tượng ghi vào audit_logs, trùng tên với dữ liệu mẫu trong script.
 */
public final class AuditActions {

    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
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

    public static final String SEND_NOTIFICATION = "SEND_NOTIFICATION";

    public static final String CREATE_REQUEST = "CREATE_REQUEST";
    public static final String CANCEL_REQUEST = "CANCEL_REQUEST";
    public static final String APPROVE_REQUEST = "APPROVE_REQUEST";
    public static final String REJECT_REQUEST = "REJECT_REQUEST";
    public static final String EXCUSE_ATTENDANCE = "EXCUSE_ATTENDANCE";

    public static final String DELETE_MESSAGE = "DELETE_MESSAGE";

    public static final String CREATE_CLUB = "CREATE_CLUB";
    public static final String UPDATE_CLUB = "UPDATE_CLUB";
    public static final String DEACTIVATE_CLUB = "DEACTIVATE_CLUB";
    public static final String ACTIVATE_CLUB = "ACTIVATE_CLUB";
    public static final String ASSIGN_CLUB_PRESIDENT = "ASSIGN_CLUB_PRESIDENT";
    public static final String APPROVE_CLUB_MEMBER = "APPROVE_CLUB_MEMBER";
    public static final String REJECT_CLUB_MEMBER = "REJECT_CLUB_MEMBER";
    public static final String CHANGE_CLUB_ROLE = "CHANGE_CLUB_ROLE";
    public static final String LEAVE_CLUB = "LEAVE_CLUB";
    public static final String CREATE_CLUB_ACTIVITY = "CREATE_CLUB_ACTIVITY";
    public static final String UPDATE_CLUB_ACTIVITY = "UPDATE_CLUB_ACTIVITY";
    public static final String CANCEL_CLUB_ACTIVITY = "CANCEL_CLUB_ACTIVITY";
    public static final String MARK_CLUB_ATTENDANCE = "MARK_CLUB_ATTENDANCE";

    public static final String EXPORT_REPORT = "EXPORT_REPORT";

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
    public static final String ENTITY_NOTIFICATION = "Notification";
    public static final String ENTITY_REQUEST = "Request";
    public static final String ENTITY_MESSAGE = "Message";
    public static final String ENTITY_CLUB = "Club";
    public static final String ENTITY_CLUB_MEMBER = "ClubMember";
    public static final String ENTITY_CLUB_ACTIVITY = "ClubActivity";

    private static final String ENTITY_PREFIX = "ENTITY_";
    private static final List<String> ACTIONS = constants(false);
    private static final List<String> ENTITY_TYPES = constants(true);

    private AuditActions() {
    }

    /** Mọi mã hành động khai báo ở trên, theo thứ tự chữ cái (ô lọc của trang nhật ký). */
    public static List<String> actions() {
        return ACTIONS;
    }

    /** Mọi loại đối tượng (hằng số ENTITY_*), theo thứ tự chữ cái. */
    public static List<String> entityTypes() {
        return ENTITY_TYPES;
    }

    /** Đọc các hằng số public static final String của lớp này, để danh sách lọc không lệch khi thêm mã mới. */
    private static List<String> constants(boolean entities) {
        return Arrays.stream(AuditActions.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers()) && f.getType() == String.class)
                .filter(f -> f.getName().startsWith(ENTITY_PREFIX) == entities)
                .map(AuditActions::value)
                .distinct().sorted().toList();
    }

    private static String value(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
