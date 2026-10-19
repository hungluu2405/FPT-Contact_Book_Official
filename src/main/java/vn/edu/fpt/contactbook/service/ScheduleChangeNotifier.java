package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Room;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Báo thay đổi lịch cho sinh viên và phụ huynh của lớp học phần (SRS FR-SCHEDULE-05). Được gọi trong transaction
 * của thao tác đổi lịch và gửi trong chính transaction đó (rollback thì không còn thông báo); actorUserId là người
 * thực hiện, cũng là người gửi thông báo.
 */
public interface ScheduleChangeNotifier {

    void sessionCancelled(Long actorUserId, ClassSession session);

    void sessionRoomChanged(Long actorUserId, ClassSession session, Room previousRoom);

    void sessionRescheduled(Long actorUserId, ClassSession session, LocalDate previousDate, LocalTime previousStart,
                            LocalTime previousEnd);

    void makeupSessionAdded(Long actorUserId, ClassSession session);
}
