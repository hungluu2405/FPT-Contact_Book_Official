package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.controller.RequestStatusFilter;
import vn.edu.fpt.contactbook.dto.AttendanceSaveResult;
import vn.edu.fpt.contactbook.dto.AttendanceSheet;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.dto.RequestListItem;
import vn.edu.fpt.contactbook.dto.ReviewResult;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;
import vn.edu.fpt.contactbook.dto.api.AttendanceSaveRequest;
import vn.edu.fpt.contactbook.dto.api.AttendanceSaveResponse;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.dto.api.ReviewRequest;
import vn.edu.fpt.contactbook.dto.api.ReviewResponse;
import vn.edu.fpt.contactbook.dto.api.SectionAttendanceResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.AttendanceService;
import vn.edu.fpt.contactbook.service.AttendanceViewService;
import vn.edu.fpt.contactbook.service.AttendanceViewService.SectionAttendance;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.RequestService.View;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.time.LocalDate;

/**
 * Chức năng giảng viên trên mobile (TEACHER, HEAD_OF_DEPARTMENT; ApiSecurityConfig): lịch dạy, lớp mình dạy, điểm danh
 * một buổi, duyệt đơn. Chỉ lớp mình dạy (Access.TEACHER, AttendanceScope) và đơn mình phụ trách (RequestService);
 * quản lý cả bộ môn, nhập điểm, gửi thông báo vẫn làm trên web.
 */
@Tag(name = "Teacher", description = "Giảng viên: lịch dạy, điểm danh, duyệt đơn")
@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherApiController {

    private final ScheduleViewService scheduleViewService;
    private final AttendanceViewService attendanceViewService;
    private final AttendanceService attendanceService;
    private final RequestService requestService;

    @Operation(summary = "Lịch dạy một tuần (date: ngày bất kỳ trong tuần, yyyy-MM-dd)")
    @GetMapping("/schedule/week")
    public InSemester<ScheduleWeek> week(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @RequestParam(required = false) Long semesterId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleViewService.week(actor, Audience.TEACHER, null, semesterId, date);
    }

    @Operation(summary = "Danh sách buổi dạy của học kỳ")
    @GetMapping("/schedule/sessions")
    public InSemester<PageResponse<ScheduleItem>> sessions(@AuthenticationPrincipal AppUserPrincipal actor,
                                                           @RequestParam(required = false) Long semesterId,
                                                           @RequestParam(defaultValue = "0") int page) {
        return paged(scheduleViewService.sessions(actor, Audience.TEACHER, null, semesterId, page));
    }

    @Operation(summary = "Lịch thi của các lớp mình dạy")
    @GetMapping("/exams")
    public InSemester<PageResponse<ExamView>> exams(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @RequestParam(required = false) Long semesterId,
                                                    @RequestParam(defaultValue = "0") int page) {
        return paged(scheduleViewService.exams(actor, Audience.TEACHER, null, semesterId, page));
    }

    @Operation(summary = "Lớp học phần mình dạy trong học kỳ")
    @GetMapping("/sections")
    public InSemester<PageResponse<TeacherSectionView>> sections(@AuthenticationPrincipal AppUserPrincipal actor,
                                                                 @RequestParam(required = false) Long semesterId,
                                                                 @RequestParam(defaultValue = "0") int page) {
        return paged(attendanceViewService.teacherSections(actor, semesterId, page));
    }

    @Operation(summary = "Thống kê điểm danh của lớp: các buổi và chuyên cần từng sinh viên")
    @GetMapping("/sections/{id}/attendance")
    public SectionAttendanceResponse sectionAttendance(@AuthenticationPrincipal AppUserPrincipal actor,
                                                       @PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int sessionPage,
                                                       @RequestParam(defaultValue = "0") int studentPage) {
        SectionAttendance data = attendanceViewService.section(actor, Access.TEACHER, id, sessionPage, studentPage);
        return new SectionAttendanceResponse(data.section(), PageResponse.from(data.sessions()),
                PageResponse.from(data.students()), data.totalSessions(), attendanceViewService.thresholdPercent());
    }

    @Operation(summary = "Bảng điểm danh của một buổi")
    @GetMapping("/sections/{id}/sessions/{sessionId}/attendance")
    public AttendanceSheet sheet(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                 @PathVariable Long sessionId) {
        return attendanceService.sheet(actor, Access.TEACHER, id, sessionId);
    }

    @Operation(summary = "Lưu điểm danh một buổi (đủ mọi sinh viên; buổi đã điểm danh thì bắt buộc có reason)")
    @PostMapping("/sections/{id}/sessions/{sessionId}/attendance")
    public AttendanceSaveResponse saveAttendance(@AuthenticationPrincipal AppUserPrincipal actor,
                                                 @PathVariable Long id, @PathVariable Long sessionId,
                                                 @Valid @RequestBody AttendanceSaveRequest body,
                                                 HttpServletRequest request) {
        AttendanceSaveResult result = attendanceService.save(actor, Access.TEACHER, id, sessionId, body.toForm(),
                request.getRemoteAddr());
        return new AttendanceSaveResponse(result.firstTime(), result.changed(),
                attendanceService.sheet(actor, Access.TEACHER, id, sessionId));
    }

    @Operation(summary = "Đơn mình phụ trách duyệt (status: PENDING mặc định, APPROVED, REJECTED, CANCELLED, ALL)")
    @GetMapping("/requests")
    public PageResponse<RequestListItem> requests(@AuthenticationPrincipal AppUserPrincipal actor,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(requestService.reviewQueue(actor, View.TEACHER, RequestStatusFilter.parse(status),
                page));
    }

    @Operation(summary = "Chi tiết đơn")
    @GetMapping("/requests/{id}")
    public RequestDetail request(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id) {
        return requestService.detail(actor, View.TEACHER, null, id);
    }

    @Operation(summary = "Duyệt hoặc từ chối đơn (từ chối bắt buộc có comment)")
    @PostMapping("/requests/{id}/review")
    public ReviewResponse review(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                 @RequestBody ReviewRequest body, HttpServletRequest request) {
        ReviewResult result = requestService.review(actor, View.TEACHER, id, body.decision(), body.comment(),
                request.getRemoteAddr());
        return new ReviewResponse(result.decision(), result.excusedSessions(),
                requestService.detail(actor, View.TEACHER, null, id));
    }

    private static <T> InSemester<PageResponse<T>> paged(InSemester<Page<T>> result) {
        return new InSemester<>(result.semester(), PageResponse.from(result.data()));
    }
}
