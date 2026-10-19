package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Danh sách lọc của trang nhật ký lấy từ chính các hằng số của AuditActions, nên mã mới tự có trong ô lọc.
 */
class AuditActionsTest {

    @Test
    @DisplayName("actions(): mọi mã hành động (kể cả mã của giai đoạn 7b), không lẫn loại đối tượng, sắp xếp, không trùng")
    void actions() {
        assertThat(AuditActions.actions())
                .contains(AuditActions.LOGIN, AuditActions.LOGIN_FAILED, AuditActions.LOGIN_LOCKED,
                        AuditActions.EXPORT_REPORT, AuditActions.DELETE_EXAM, AuditActions.MARK_CLUB_ATTENDANCE)
                .doesNotContain(AuditActions.ENTITY_USER, AuditActions.ENTITY_CLUB_ACTIVITY)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("entityTypes(): mọi hằng số ENTITY_*, không lẫn mã hành động")
    void entityTypes() {
        assertThat(AuditActions.entityTypes())
                .contains(AuditActions.ENTITY_USER, AuditActions.ENTITY_CLASS_SECTION, AuditActions.ENTITY_SUBJECT,
                        AuditActions.ENTITY_CLUB_ACTIVITY)
                .doesNotContain(AuditActions.LOGIN)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.naturalOrder());
    }
}
