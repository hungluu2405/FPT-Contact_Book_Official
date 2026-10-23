package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.ClubMemberRow;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;

import java.time.LocalDateTime;

/** Một thành viên đang tham gia: chỉ họ tên, vai trò trong CLB và thời điểm tham gia (như trang chi tiết CLB của web). */
public record ClubMemberResponse(String fullName, ClubMemberRole role, LocalDateTime joinedAt) {

    public static ClubMemberResponse from(ClubMemberRow row) {
        return new ClubMemberResponse(row.fullName(), row.role(), row.joinedAt());
    }
}
