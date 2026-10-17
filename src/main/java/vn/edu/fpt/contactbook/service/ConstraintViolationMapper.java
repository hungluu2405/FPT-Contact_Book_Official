package vn.edu.fpt.contactbook.service;

import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.util.Locale;
import java.util.Map;

/**
 * Đổi lỗi UNIQUE của SQL Server (thường do hai người thao tác cùng lúc, lọt qua bước kiểm tra trước)
 * thành lỗi tiếng Việt gắn với ô trên form. Không đưa nội dung lỗi SQL ra ngoài.
 */
public final class ConstraintViolationMapper {

    /** Tên ràng buộc trong database/fpt_contact_book.sql → (ô trên form, thông báo). */
    private static final Map<String, String[]> CONSTRAINTS = Map.of(
            "uq_users_phone", new String[]{"phone", UserMessages.PHONE_TAKEN},
            "ux_users_email", new String[]{"email", UserMessages.EMAIL_TAKEN},
            "uq_students_code", new String[]{"profile.studentCode", UserMessages.STUDENT_CODE_TAKEN},
            "uq_teachers_code", new String[]{"profile.teacherCode", UserMessages.TEACHER_CODE_TAKEN},
            "uq_user_roles", new String[]{"roleCode", UserMessages.ROLE_ALREADY_ASSIGNED},
            "uq_students_user", new String[]{null, UserMessages.PROFILE_EXISTS},
            "uq_teachers_user", new String[]{null, UserMessages.PROFILE_EXISTS},
            "uq_parents_user", new String[]{null, UserMessages.PROFILE_EXISTS},
            "pk_parent_student", new String[]{"otherKey", UserMessages.LINK_EXISTS});

    private ConstraintViolationMapper() {
    }

    /** Lỗi ràng buộc đã biết → BusinessException; lỗi khác giữ nguyên để GlobalExceptionHandler xử lý. */
    public static RuntimeException map(DataIntegrityViolationException exception) {
        String message = fullMessage(exception).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : CONSTRAINTS.entrySet()) {
            if (message.contains("'" + entry.getKey() + "'")) {
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
