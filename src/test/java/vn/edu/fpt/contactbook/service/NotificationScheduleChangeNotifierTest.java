package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đổi lịch → thông báo tới SV ENROLLED và PH của lớp học phần, người gửi là người đổi lịch.
 */
@ExtendWith(MockitoExtension.class)
class NotificationScheduleChangeNotifierTest {

    @Mock NotificationService notificationService;
    @Mock NotificationAudience audience;

    private NotificationScheduleChangeNotifier notifier;
    private ClassSession session;

    @BeforeEach
    void setUp() {
        notifier = new NotificationScheduleChangeNotifier(notificationService, audience);
        Department se = CatalogTestData.department(1L, "SE");
        ClassSection section = CatalogTestData.section(10L, "TOAN10-01", CatalogTestData.subject(1L, "TOAN10", se),
                CatalogTestData.semester(1L, "HK1-2627"), CatalogTestData.teacher(2L, "GV002", se), 40,
                SectionStatus.OPEN);
        session = new ClassSession();
        session.setClassSection(section);
        session.setSessionNo((short) 3);
        session.setSessionDate(LocalDate.of(2026, 10, 26));
        session.setStartTime(LocalTime.of(7, 30));
        session.setEndTime(LocalTime.of(9, 45));
        when(audience.recipients(NotificationTargetType.CLASS_SECTION, 10L)).thenReturn(Set.of(4L, 5L));
    }

    @Test
    @DisplayName("Hủy buổi: thông báo quan trọng cho HS và PH của lớp")
    void cancelled() {
        notifier.sessionCancelled(1L, session);

        verify(notificationService).sendAutomatic(eq(1L), eq("[TOAN10-01] Hủy buổi 3 ngày 26/10/2026"),
                contains("26/10/2026 07:30–09:45 đã bị hủy"), eq(NotificationPriority.IMPORTANT),
                eq(NotificationTargetType.CLASS_SECTION), eq(10L), eq(Set.of(4L, 5L)));
    }

    @Test
    @DisplayName("Đổi phòng, đổi giờ, buổi bù đều gửi cho lớp")
    void otherChanges() {
        Room r101 = new Room();
        r101.setCode("R101");
        session.setRoom(r101);

        notifier.sessionRoomChanged(1L, session, null);
        notifier.sessionRescheduled(1L, session, LocalDate.of(2026, 10, 25), LocalTime.of(13, 0),
                LocalTime.of(15, 15));
        notifier.makeupSessionAdded(1L, session);

        verify(notificationService).sendAutomatic(eq(1L), contains("Đổi phòng"),
                contains("từ phòng (chưa xếp phòng) sang phòng R101"), eq(NotificationPriority.NORMAL),
                eq(NotificationTargetType.CLASS_SECTION), eq(10L), eq(Set.of(4L, 5L)));
        verify(notificationService).sendAutomatic(eq(1L), contains("Đổi lịch"),
                contains("từ 25/10/2026 13:00–15:15 sang 26/10/2026 07:30–09:45"), eq(NotificationPriority.IMPORTANT),
                eq(NotificationTargetType.CLASS_SECTION), eq(10L), eq(Set.of(4L, 5L)));
        verify(notificationService).sendAutomatic(eq(1L), contains("Buổi học bù"), contains("phòng R101"),
                eq(NotificationPriority.NORMAL), eq(NotificationTargetType.CLASS_SECTION), eq(10L),
                eq(Set.of(4L, 5L)));
    }
}
