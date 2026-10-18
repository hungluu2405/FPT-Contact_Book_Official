package vn.edu.fpt.contactbook.service;

import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.util.Locale;
import java.util.Map;

/**
 * Đổi lỗi UNIQUE, CHECK, FOREIGN KEY của SQL Server (thường do hai người thao tác cùng lúc, lọt qua bước
 * kiểm tra trước) thành lỗi tiếng Việt gắn với ô trên form. Không đưa nội dung lỗi SQL ra ngoài.
 */
public final class ConstraintViolationMapper {

    /** Tên ràng buộc trong database/fpt_contact_book.sql → (ô trên form, thông báo). */
    private static final Map<String, String[]> CONSTRAINTS = Map.ofEntries(
            Map.entry("uq_users_phone", new String[]{"phone", UserMessages.PHONE_TAKEN}),
            Map.entry("ux_users_email", new String[]{"email", UserMessages.EMAIL_TAKEN}),
            Map.entry("uq_students_code", new String[]{"profile.studentCode", UserMessages.STUDENT_CODE_TAKEN}),
            Map.entry("uq_teachers_code", new String[]{"profile.teacherCode", UserMessages.TEACHER_CODE_TAKEN}),
            Map.entry("uq_user_roles", new String[]{"roleCode", UserMessages.ROLE_ALREADY_ASSIGNED}),
            Map.entry("uq_students_user", new String[]{null, UserMessages.PROFILE_EXISTS}),
            Map.entry("uq_teachers_user", new String[]{null, UserMessages.PROFILE_EXISTS}),
            Map.entry("uq_parents_user", new String[]{null, UserMessages.PROFILE_EXISTS}),
            Map.entry("pk_parent_student", new String[]{"otherKey", UserMessages.LINK_EXISTS}),
            Map.entry("uq_faculties_code", new String[]{"code", CatalogMessages.FACULTY_CODE_TAKEN}),
            Map.entry("uq_departments_code", new String[]{"code", CatalogMessages.DEPARTMENT_CODE_TAKEN}),
            Map.entry("uq_majors_code", new String[]{"code", CatalogMessages.MAJOR_CODE_TAKEN}),
            Map.entry("uq_classes_code", new String[]{"code", CatalogMessages.CLASS_CODE_TAKEN}),
            Map.entry("uq_subjects_code", new String[]{"code", CatalogMessages.SUBJECT_CODE_TAKEN}),
            Map.entry("uq_semesters_code", new String[]{"code", CatalogMessages.SEMESTER_CODE_TAKEN}),
            Map.entry("uq_rooms_code", new String[]{"code", CatalogMessages.ROOM_CODE_TAKEN}),
            Map.entry("uq_grade_components", new String[]{"name", CatalogMessages.COMPONENT_NAME_TAKEN}),
            Map.entry("ux_semesters_current", new String[]{null, CatalogMessages.CURRENT_SEMESTER_CONFLICT}),
            Map.entry("ck_subjects_credits", new String[]{"credits", CatalogMessages.CREDITS_RANGE}),
            Map.entry("ck_gc_weight", new String[]{"weight", CatalogMessages.WEIGHT_RANGE}),
            Map.entry("ck_semesters_dates", new String[]{"endDate", CatalogMessages.SEMESTER_DATES}),
            Map.entry("ck_rooms_capacity", new String[]{"capacity", CatalogMessages.ROOM_CAPACITY}),
            Map.entry("fk_grades_component", new String[]{null, CatalogMessages.COMPONENT_HAS_GRADES}),
            Map.entry("uq_enrollments", new String[]{null, SectionMessages.ALREADY_ENROLLED}),
            Map.entry("ck_cs_max", new String[]{"maxStudents", SectionMessages.MAX_STUDENTS_RANGE}),
            Map.entry("ux_sess_room_slot", new String[]{null, ScheduleMessages.ROOM_SLOT_TAKEN}),
            Map.entry("uq_sess_cs_slot", new String[]{null, ScheduleMessages.SECTION_SLOT_TAKEN}),
            Map.entry("ck_sess_time", new String[]{"endTime", ScheduleMessages.END_AFTER_START}),
            Map.entry("ck_exams_time", new String[]{"endTime", ScheduleMessages.END_AFTER_START}));

    private ConstraintViolationMapper() {
    }

    /** Lỗi ràng buộc đã biết → BusinessException; lỗi khác giữ nguyên để GlobalExceptionHandler xử lý. */
    public static RuntimeException map(DataIntegrityViolationException exception) {
        String message = fullMessage(exception).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : CONSTRAINTS.entrySet()) {
            // UNIQUE báo tên trong dấu '...', còn CHECK và FOREIGN KEY báo trong dấu "...".
            if (message.contains("'" + entry.getKey() + "'") || message.contains("\"" + entry.getKey() + "\"")) {
                String[] target = entry.getValue();
                return new BusinessException(target[1], target[0]);
            }
        }
        return exception;
    }

    private static String fullMessage(Throwable exception) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = exception; current != null; current = current.getCause()) {
            text.append(current.getMessage()).append('\n');
            if (current.getCause() == current) {
                break;
            }
        }
        return text.toString();
    }
}
