package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.ActivityView;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;

import java.time.LocalDateTime;

/**
 * Một hoạt động sắp tới của câu lạc bộ; myStatus là đăng ký của người xem (null khi chưa đăng ký). API chỉ đọc nên
 * bỏ các cờ đăng ký, sửa, ghi nhận tham dự của trang web.
 */
public record ClubActivityResponse(Long id, String title, String description, String location,
                                   LocalDateTime startTime, LocalDateTime endTime, Short maxParticipants, long taken,
                                   ClubActivityStatus state, ParticipantStatus myStatus) {

    public static ClubActivityResponse from(ActivityView activity) {
        return new ClubActivityResponse(activity.id(), activity.title(), activity.description(), activity.location(),
                activity.startTime(), activity.endTime(), activity.maxParticipants(), activity.taken(),
                activity.state(), activity.myStatus());
    }
}
