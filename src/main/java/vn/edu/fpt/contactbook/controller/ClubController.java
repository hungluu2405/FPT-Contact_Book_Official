package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.ClubDetail;
import vn.edu.fpt.contactbook.dto.ClubMemberRow;
import vn.edu.fpt.contactbook.dto.ClubView;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ClubActivityService;
import vn.edu.fpt.contactbook.service.ClubLogoStorage;
import vn.edu.fpt.contactbook.service.ClubMembershipService;
import vn.edu.fpt.contactbook.service.ClubService;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.time.Duration;

/**
 * Câu lạc bộ cho mọi vai trò (SRS FR-CLUB-02..04, 08): danh sách CLB đang hoạt động, chi tiết, "CLB của tôi" (sinh
 * viên), gửi yêu cầu tham gia, rời CLB, trang duyệt thành viên (chủ nhiệm, ADMIN), logo. Quyền kiểm tra ở service.
 */
@Controller
@RequiredArgsConstructor
public class ClubController {

    static final String BASE = "/clubs";

    private final ClubService clubService;
    private final ClubMembershipService membershipService;
    private final ClubActivityService activityService;
    private final ClubLogoStorage logoStorage;

    @GetMapping(BASE)
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<ClubView> clubs = clubService.activeClubs(q, page);
        model.addAttribute("clubs", clubs);
        model.addAttribute("q", q);
        model.addAttribute("pager", PageUtils.pager(clubs, BASE, PageUtils.params("q", q)));
        model.addAttribute("activeMenu", "clubs");
        return "clubs/list";
    }

    @GetMapping(BASE + "/my")
    public String my(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("items", clubService.myClubs(actor));
        model.addAttribute("activeMenu", "clubs-my");
        return "clubs/my";
    }

    @GetMapping(BASE + "/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        ClubDetail detail = clubService.view(actor, id);
        Page<ClubMemberRow> members = clubService.members(id, page);
        model.addAttribute("detail", detail);
        model.addAttribute("members", members);
        model.addAttribute("pager", PageUtils.pager(members, BASE + "/" + id, PageUtils.params()));
        model.addAttribute("activities", activityService.upcoming(actor, id));
        model.addAttribute("activeMenu", "clubs");
        return "clubs/detail";
    }

    @PostMapping(BASE + "/{id:\\d+}/join")
    public String join(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes,
                "Đã gửi yêu cầu tham gia, vui lòng chờ chủ nhiệm duyệt", () -> membershipService.join(actor, id));
    }

    @PostMapping(BASE + "/{id:\\d+}/leave")
    public String leave(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes, "Bạn đã rời câu lạc bộ",
                () -> membershipService.leave(actor, id, request.getRemoteAddr()));
    }

    /** Trang quản lý thành viên: status PENDING (mặc định, yêu cầu chờ duyệt) hoặc ACTIVE (đổi vai trò). */
    @GetMapping(BASE + "/{id:\\d+}/members")
    public String members(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                          @RequestParam(defaultValue = "PENDING") ClubMemberStatus status,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        Page<ClubMemberRow> rows = membershipService.requests(actor, id, status, page);
        model.addAttribute("detail", clubService.view(actor, id));
        model.addAttribute("rows", rows);
        model.addAttribute("status", status);
        model.addAttribute("pager", PageUtils.pager(rows, BASE + "/" + id + "/members",
                PageUtils.params("status", status)));
        model.addAttribute("activeMenu", "clubs");
        return "clubs/members";
    }

    @PostMapping(BASE + "/{id:\\d+}/members/{memberId:\\d+}/approve")
    public String approve(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                          @PathVariable Long memberId, HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id + "/members", redirectAttributes, "Đã duyệt thành viên",
                () -> membershipService.approve(actor, id, memberId, request.getRemoteAddr()));
    }

    @PostMapping(BASE + "/{id:\\d+}/members/{memberId:\\d+}/reject")
    public String reject(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @PathVariable Long memberId, HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id + "/members", redirectAttributes, "Đã từ chối yêu cầu",
                () -> membershipService.reject(actor, id, memberId, request.getRemoteAddr()));
    }

    @PostMapping(BASE + "/{id:\\d+}/members/{memberId:\\d+}/role")
    public String changeRole(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                             @PathVariable Long memberId, @RequestParam ClubMemberRole role,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id + "/members?status=ACTIVE", redirectAttributes,
                "Đã đổi vai trò",
                () -> membershipService.changeRole(actor, id, memberId, role, request.getRemoteAddr()));
    }

    /** Logo chỉ trả cho người đã đăng nhập (SecurityConfig: anyRequest().authenticated()). */
    @GetMapping(ClubLogoStorage.URL_PREFIX + "{fileName}")
    public ResponseEntity<Resource> logo(@PathVariable String fileName) {
        return logoStorage.resolve(fileName)
                .map(file -> ResponseEntity.ok()
                        .contentType(fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .<Resource>body(new FileSystemResource(file)))
                .orElse(ResponseEntity.notFound().build());
    }
}
