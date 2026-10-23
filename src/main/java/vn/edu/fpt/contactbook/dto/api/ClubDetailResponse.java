package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.ClubDetail;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;

/**
 * Chi tiết câu lạc bộ và quan hệ của người xem: myStatus, myRole null khi chưa từng gửi yêu cầu. API chỉ đọc nên
 * bỏ các cờ quản lý (duyệt thành viên, quản lý hoạt động) của trang web.
 */
public record ClubDetailResponse(ClubResponse club, ClubMemberStatus myStatus, ClubMemberRole myRole) {

    public static ClubDetailResponse from(ClubDetail detail) {
        return new ClubDetailResponse(ClubResponse.from(detail.club()), detail.myStatus(), detail.myRole());
    }
}
