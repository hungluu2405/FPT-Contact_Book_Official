package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubStatus;

import java.time.LocalDate;

/**
 * Câu lạc bộ để hiển thị: giảng viên phụ trách, số thành viên ACTIVE, chủ nhiệm hiện tại (null nếu chưa có).
 */
public record ClubView(Long id, String code, String name, String description, String logoUrl, LocalDate foundedDate,
                       Long advisorUserId, String advisorName, ClubStatus status, long memberCount,
                       String presidentName) {
}
