package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;

import java.time.LocalDateTime;

/**
 * Một dòng thành viên hoặc yêu cầu tham gia (club_members); createdAt là lúc gửi yêu cầu.
 */
public record ClubMemberRow(Long id, Long userId, String fullName, ClubMemberRole role, ClubMemberStatus status,
                            LocalDateTime joinedAt, LocalDateTime createdAt) {
}
