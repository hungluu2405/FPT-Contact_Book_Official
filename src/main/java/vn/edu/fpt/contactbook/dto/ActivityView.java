package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;

import java.time.LocalDateTime;

/**
 * Hoạt động CLB để hiển thị. state là trạng thái tính theo thời gian (ClubRule.state); taken là số chỗ đã giữ
 * (REGISTERED + ATTENDED); myStatus là đăng ký của người xem (null nếu chưa có). Các cờ can* quyết định nút bấm,
 * service vẫn kiểm tra lại khi thao tác.
 */
public record ActivityView(Long id, Long clubId, String title, String description, String location,
                           LocalDateTime startTime, LocalDateTime endTime, Short maxParticipants, long taken,
                           ClubActivityStatus state, ParticipantStatus myStatus, boolean canRegister,
                           boolean canUnregister, boolean canEdit, boolean canMarkAttendance) {
}
