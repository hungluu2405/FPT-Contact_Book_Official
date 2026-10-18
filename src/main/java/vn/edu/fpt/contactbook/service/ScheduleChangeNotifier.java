package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Room;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Báo thay đổi lịch cho giảng viên, sinh viên, phụ huynh (SRS FR-SCHEDULE-05). Được gọi trong transaction của
 * thao tác đổi lịch; cài đặt gửi thông báo thật (giai đoạn Thông báo) nên chỉ gửi sau khi transaction commit.
 */
public interface ScheduleChangeNotifier {

    void sessionCancelled(ClassSession session);

    void sessionRoomChanged(ClassSession session, Room previousRoom);

    void sessionRescheduled(ClassSession session, LocalDate previousDate, LocalTime previousStart,
                            LocalTime previousEnd);

    void makeupSessionAdded(ClassSession session);
}
