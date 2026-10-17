package vn.edu.fpt.contactbook.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mốc thời gian do entity tự gán phải theo giờ Asia/Ho_Chi_Minh kể cả khi JVM chạy ở UTC.
 */
class EntityTimestampZoneTest {

    private TimeZone original;

    @BeforeEach
    void useUtcAsJvmDefault() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreJvmDefault() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("created_at và updated_at dùng giờ Việt Nam khi JVM ở UTC")
    void timestampsUseVietnamTimeOnUtcJvm() {
        User user = new User();

        user.prePersist();
        user.preUpdate();

        LocalDateTime vietnamNow = LocalDateTime.now(AppTime.ZONE);
        assertThat(Duration.between(user.getCreatedAt(), vietnamNow).abs()).isLessThan(Duration.ofMinutes(1));
        assertThat(Duration.between(user.getUpdatedAt(), vietnamNow).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
