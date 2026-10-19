package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Báo thay đổi lịch (SRS FR-SCHEDULE-05) bằng thông báo hệ thống tới sinh viên ENROLLED và phụ huynh đã liên kết của
 * lớp học phần; người gửi là người đổi lịch. Gửi trong transaction của thao tác đổi lịch (NotificationService).
 */
@Component
@RequiredArgsConstructor
public class NotificationScheduleChangeNotifier implements ScheduleChangeNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;
    private final NotificationAudience audience;

    @Override
    public void sessionCancelled(Long actorUserId, ClassSession session) {
        send(actorUserId, session, NotificationPriority.IMPORTANT,
                "Hủy buổi " + session.getSessionNo() + " ngày " + date(session.getSessionDate()),
                "Buổi " + session.getSessionNo() + " " + subject(session) + " ngày " + slot(session.getSessionDate(),
                        session.getStartTime(), session.getEndTime()) + " đã bị hủy.");
    }

    @Override
    public void sessionRoomChanged(Long actorUserId, ClassSession session, Room previousRoom) {
        send(actorUserId, session, NotificationPriority.NORMAL,
                "Đổi phòng buổi " + session.getSessionNo() + " ngày " + date(session.getSessionDate()),
                "Buổi " + session.getSessionNo() + " " + subject(session) + " ngày " + slot(session.getSessionDate(),
                        session.getStartTime(), session.getEndTime()) + " chuyển từ phòng " + room(previousRoom)
                        + " sang phòng " + room(session.getRoom()) + ".");
    }

    @Override
    public void sessionRescheduled(Long actorUserId, ClassSession session, LocalDate previousDate,
                                   LocalTime previousStart, LocalTime previousEnd) {
        send(actorUserId, session, NotificationPriority.IMPORTANT,
                "Đổi lịch buổi " + session.getSessionNo() + " sang ngày " + date(session.getSessionDate()),
                "Buổi " + session.getSessionNo() + " " + subject(session) + " chuyển từ " + slot(previousDate,
                        previousStart, previousEnd) + " sang " + slot(session.getSessionDate(),
                        session.getStartTime(), session.getEndTime()) + ", phòng " + room(session.getRoom()) + ".");
    }

    @Override
    public void makeupSessionAdded(Long actorUserId, ClassSession session) {
        send(actorUserId, session, NotificationPriority.NORMAL,
                "Buổi học bù ngày " + date(session.getSessionDate()),
                "Thêm buổi học bù " + session.getSessionNo() + " " + subject(session) + " ngày "
                        + slot(session.getSessionDate(), session.getStartTime(), session.getEndTime()) + ", phòng "
                        + room(session.getRoom()) + ".");
    }

    private void send(Long actorUserId, ClassSession session, NotificationPriority priority, String title,
                      String content) {
        ClassSection section = session.getClassSection();
        notificationService.sendAutomatic(actorUserId, "[" + section.getCode() + "] " + title, content, priority,
                NotificationTargetType.CLASS_SECTION, section.getId(),
                audience.recipients(NotificationTargetType.CLASS_SECTION, section.getId()));
    }

    private static String subject(ClassSession session) {
        ClassSection section = session.getClassSection();
        return "môn " + section.getSubject().getCode() + " - " + section.getSubject().getName() + " (lớp "
                + section.getCode() + ")";
    }

    private static String slot(LocalDate date, LocalTime start, LocalTime end) {
        return date(date) + " " + start.format(TIME) + "–" + end.format(TIME);
    }

    private static String date(LocalDate date) {
        return date.format(DATE);
    }

    private static String room(Room room) {
        return room == null ? "(chưa xếp phòng)" : room.getCode();
    }
}
