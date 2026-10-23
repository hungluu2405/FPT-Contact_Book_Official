package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.api.ClubActivityResponse;
import vn.edu.fpt.contactbook.dto.api.ClubDetailResponse;
import vn.edu.fpt.contactbook.dto.api.ClubMemberResponse;
import vn.edu.fpt.contactbook.dto.api.ClubResponse;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ClubActivityService;
import vn.edu.fpt.contactbook.service.ClubService;

import java.util.List;

/**
 * Câu lạc bộ cho mobile, chỉ đọc (mọi vai trò đã đăng nhập, như trang /clubs của web). Gửi yêu cầu tham gia, đăng ký
 * hoạt động và các thao tác quản lý vẫn làm trên web. "CLB của tôi" nằm ở /student/clubs và /parent/children/{id}/clubs.
 */
@Tag(name = "Clubs", description = "Câu lạc bộ (chỉ đọc)")
@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubApiController {

    private final ClubService clubService;
    private final ClubActivityService activityService;

    @Operation(summary = "Câu lạc bộ đang hoạt động; q tìm theo mã hoặc tên")
    @GetMapping
    public PageResponse<ClubResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(clubService.activeClubs(q, page), ClubResponse::from);
    }

    @Operation(summary = "Chi tiết câu lạc bộ và quan hệ của người xem; CLB ngừng hoạt động vẫn xem được")
    @GetMapping("/{id}")
    public ClubDetailResponse detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id) {
        return ClubDetailResponse.from(clubService.view(actor, id));
    }

    @Operation(summary = "Thành viên đang tham gia, chủ nhiệm trước")
    @GetMapping("/{id}/members")
    public PageResponse<ClubMemberResponse> members(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(clubService.members(id, page), ClubMemberResponse::from);
    }

    @Operation(summary = "Hoạt động sắp tới (chưa hủy, chưa kết thúc) kèm đăng ký của người xem")
    @GetMapping("/{id}/activities")
    public List<ClubActivityResponse> activities(@AuthenticationPrincipal AppUserPrincipal actor,
                                                 @PathVariable Long id) {
        return activityService.upcoming(actor, id).stream().map(ClubActivityResponse::from).toList();
    }
}
