package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.contactbook.controller.Uploads;
import vn.edu.fpt.contactbook.dto.HeadDepartmentView;
import vn.edu.fpt.contactbook.dto.HeadNotificationForm;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.dto.api.SentNotificationDetailResponse;
import vn.edu.fpt.contactbook.dto.api.TeacherDetailResponse;
import vn.edu.fpt.contactbook.dto.api.TeacherSummaryResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.DepartmentHeadService;
import vn.edu.fpt.contactbook.service.NotificationService;

import java.io.IOException;
import java.util.List;

/** Tổ trưởng chuyên môn: tổ phụ trách, giáo viên trong tổ, thông báo nội bộ. Phạm vi tổ kiểm tra ở service. */
@Tag(name = "Head of department", description = "Tổ trưởng chuyên môn")
@RestController
@RequestMapping("/api/v1/head")
@RequiredArgsConstructor
public class HeadApiController {

    private final DepartmentHeadService departmentHeadService;
    private final NotificationService notificationService;

    @Operation(summary = "Các tổ mình làm tổ trưởng, kèm số giáo viên")
    @GetMapping("/departments")
    public List<HeadDepartmentView> departments(@AuthenticationPrincipal AppUserPrincipal actor) {
        return departmentHeadService.departments(actor);
    }

    @Operation(summary = "Giáo viên trong tổ (departmentId trống: mọi tổ mình phụ trách), tìm theo tên, mã, số điện thoại")
    @GetMapping("/teachers")
    public PageResponse<TeacherSummaryResponse> teachers(@AuthenticationPrincipal AppUserPrincipal actor,
                                                         @RequestParam(required = false) Long departmentId,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(departmentHeadService.teachers(actor, departmentId, q, page),
                TeacherSummaryResponse::from);
    }

    @Operation(summary = "Chi tiết một giáo viên trong tổ")
    @GetMapping("/teachers/{userId}")
    public TeacherDetailResponse teacher(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long userId) {
        return TeacherDetailResponse.from(departmentHeadService.teacher(actor, userId));
    }

    @Operation(summary = "Gửi thông báo nội bộ (multipart/form-data): cả tổ departmentId hoặc riêng teacherUserId")
    @PostMapping(value = "/notifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SentNotificationDetailResponse send(@AuthenticationPrincipal AppUserPrincipal actor,
                                               @ModelAttribute HeadNotificationForm form,
                                               @RequestParam(value = "files", required = false)
                                               List<MultipartFile> files,
                                               HttpServletRequest request) throws IOException {
        Long id = departmentHeadService.send(actor, form, Uploads.of(files), request.getRemoteAddr());
        return SentNotificationDetailResponse.from(notificationService.sentDetail(actor, id, null, 0));
    }
}
