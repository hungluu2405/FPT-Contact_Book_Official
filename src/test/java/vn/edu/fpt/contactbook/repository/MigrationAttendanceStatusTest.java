package vn.edu.fpt.contactbook.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.fpt.contactbook.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V002: điểm danh còn ba trạng thái. Dòng LATE của dữ liệu mẫu (id 13, 16) thành PRESENT, giữ ghi chú;
 * ràng buộc ck_att_status không còn nhận LATE.
 */
@IntegrationTest
class MigrationAttendanceStatusTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Không còn dòng LATE; dòng đi muộn cũ thành PRESENT và giữ ghi chú")
    void lateRowsBecomePresent() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.attendance WHERE status = N'LATE'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT status + '|' + note FROM dbo.attendance WHERE id IN (13, 16) "
                + "ORDER BY id", String.class))
                .containsExactly("PRESENT|Muộn 10 phút do xe hỏng", "PRESENT|Đi muộn 15p");
    }

    @Test
    @DisplayName("CHECK chỉ nhận PRESENT, ABSENT_EXCUSED, ABSENT_UNEXCUSED: ghi LATE bị từ chối")
    void checkRejectsLate() {
        String definition = jdbcTemplate.queryForObject("SELECT definition FROM sys.check_constraints "
                + "WHERE name = N'ck_att_status'", String.class);
        assertThat(definition).contains("PRESENT", "ABSENT_EXCUSED", "ABSENT_UNEXCUSED").doesNotContain("LATE");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE dbo.attendance SET status = N'LATE' WHERE id = 13"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
