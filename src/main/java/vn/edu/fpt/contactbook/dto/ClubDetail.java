package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;

/**
 * Trang chi tiết CLB: thông tin CLB và quan hệ của người xem với CLB. myStatus / myRole null khi người xem chưa có
 * dòng club_members; canJoin chỉ đúng với sinh viên được gửi yêu cầu; canManageMembers (chủ nhiệm, ADMIN) và
 * canManageActivities (chủ nhiệm, phó chủ nhiệm, ADMIN) quyết định các nút quản lý.
 */
public record ClubDetail(ClubView club, ClubMemberStatus myStatus, ClubMemberRole myRole, boolean canJoin,
                         boolean canLeave, boolean canManageMembers, boolean canManageActivities) {
}
