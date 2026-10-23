package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;

/**
 * Thông báo nội bộ của tổ trưởng: teacherUserId khác null là gửi riêng một giáo viên trong tổ, ngược lại gửi cả tổ
 * departmentId. Mọi giá trị kiểm tra ở NotificationService như form web.
 */
@Getter
@Setter
@NoArgsConstructor
public class HeadNotificationForm {

    private String title;
    private String content;
    private NotificationPriority priority = NotificationPriority.NORMAL;
    private Long departmentId;
    private Long teacherUserId;
}
