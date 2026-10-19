package vn.edu.fpt.contactbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

/**
 * Form gửi thông báo. Mỗi phạm vi có một ô chọn riêng (roleId, departmentId, classId, sectionId) để form vẫn đúng
 * khi trình duyệt tắt JavaScript; với USER người nhận nhập bằng số điện thoại (recipientPhone). targetId() là đích
 * của phạm vi đã chọn. Mọi giá trị đều được kiểm tra lại ở service.
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationForm {

    @NotBlank(message = "{notification.title.required}")
    @Size(max = 200, message = "{notification.title.size}")
    private String title;

    @NotBlank(message = "{notification.content.required}")
    private String content;

    @NotNull(message = "{notification.priority.required}")
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @NotNull(message = "{notification.target.required}")
    private NotificationTargetType targetType;

    private Long roleId;

    private Long departmentId;

    private Long classId;

    private Long sectionId;

    private String recipientPhone;

    /** Đích của phạm vi đã chọn (target_id); null với ALL, USER hoặc khi chưa chọn phạm vi. */
    public Long targetId() {
        if (targetType == null) {
            return null;
        }
        return switch (targetType) {
            case ROLE -> roleId;
            case DEPARTMENT -> departmentId;
            case CLASS -> classId;
            case CLASS_SECTION -> sectionId;
            case ALL, USER -> null;
        };
    }

    /** Tên ô trên form của đích theo phạm vi, để gắn lỗi cạnh đúng ô. */
    public String targetField() {
        if (targetType == null) {
            return "targetType";
        }
        return switch (targetType) {
            case ROLE -> "roleId";
            case DEPARTMENT -> "departmentId";
            case CLASS -> "classId";
            case CLASS_SECTION -> "sectionId";
            case USER -> "recipientPhone";
            case ALL -> "targetType";
        };
    }
}
