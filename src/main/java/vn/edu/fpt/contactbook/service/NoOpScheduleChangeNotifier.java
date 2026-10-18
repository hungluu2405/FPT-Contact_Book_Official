package vn.edu.fpt.contactbook.service;

import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Room;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Cài đặt tạm của ScheduleChangeNotifier: chưa gửi gì. Giai đoạn Thông báo sẽ thay bằng cài đặt thật.
 */
@Component
public class NoOpScheduleChangeNotifier implements ScheduleChangeNotifier {

    @Override
    public void sessionCancelled(ClassSession session) {
        // Chưa có phân hệ thông báo.
    }

    @Override
    public void sessionRoomChanged(ClassSession session, Room previousRoom) {
        // Chưa có phân hệ thông báo.
    }

    @Override
    public void sessionRescheduled(ClassSession session, LocalDate previousDate, LocalTime previousStart,
                                   LocalTime previousEnd) {
        // Chưa có phân hệ thông báo.
    }

    @Override
    public void makeupSessionAdded(ClassSession session) {
        // Chưa có phân hệ thông báo.
    }
}
