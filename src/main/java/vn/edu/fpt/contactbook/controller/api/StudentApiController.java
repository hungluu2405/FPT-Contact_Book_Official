package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.LeaveFormOptions;
import vn.edu.fpt.contactbook.dto.LeaveRequestForm;
import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.dto.RequestListItem;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.StudentGradeRow;
import vn.edu.fpt.contactbook.dto.api.AttendanceHistoryResponse;
import vn.edu.fpt.contactbook.dto.api.AttendanceOverview;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Dữ liệu của chính sinh viên đang đăng nhập (STUDENT, ApiSecurityConfig). semesterId bỏ trống: học kỳ hiện tại. */
@Tag(name = "Student", description = "Sinh viên xem dữ liệu của mình, gửi và hủy đơn nghỉ")
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentApiController {

    private final StudentDataApi data;

    @Operation(summary = "Thời khóa biểu một tuần (date: ngày bất kỳ trong tuần, yyyy-MM-dd)")
    @GetMapping("/schedule/week")
    public InSemester<ScheduleWeek> week(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @RequestParam(required = false) Long semesterId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return data.week(actor, Audience.STUDENT, null, semesterId, date);
    }

    @Operation(summary = "Danh sách buổi học của học kỳ")
    @GetMapping("/schedule/sessions")
    public InSemester<PageResponse<ScheduleItem>> sessions(@AuthenticationPrincipal AppUserPrincipal actor,
                                                           @RequestParam(required = false) Long semesterId,
                                                           @RequestParam(defaultValue = "0") int page) {
        return data.sessions(actor, Audience.STUDENT, null, semesterId, page);
    }

    @Operation(summary = "Lịch thi của học kỳ")
    @GetMapping("/exams")
    public InSemester<PageResponse<ExamView>> exams(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @RequestParam(required = false) Long semesterId,
                                                    @RequestParam(defaultValue = "0") int page) {
        return data.exams(actor, Audience.STUDENT, null, semesterId, page);
    }

    @Operation(summary = "Điểm theo môn (chỉ đầu điểm đã công bố)")
    @GetMapping("/grades")
    public InSemester<List<StudentGradeRow>> grades(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @RequestParam(required = false) Long semesterId) {
        return data.grades(actor, Audience.STUDENT, null, semesterId);
    }

    @Operation(summary = "Chuyên cần theo môn")
    @GetMapping("/attendance")
    public AttendanceOverview attendance(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @RequestParam(required = false) Long semesterId) {
        return data.attendance(actor, Audience.STUDENT, null, semesterId);
    }

    @Operation(summary = "Điểm danh từng buổi của một lớp học phần")
    @GetMapping("/attendance/{sectionId}")
    public AttendanceHistoryResponse attendanceHistory(@AuthenticationPrincipal AppUserPrincipal actor,
                                                       @PathVariable Long sectionId,
                                                       @RequestParam(defaultValue = "0") int page) {
        return data.attendanceHistory(actor, Audience.STUDENT, null, sectionId, page);
    }

    @Operation(summary = "Danh sách đơn (mọi loại đơn)")
    @GetMapping("/requests")
    public PageResponse<RequestListItem> requests(@AuthenticationPrincipal AppUserPrincipal actor,
                                                  @RequestParam(defaultValue = "0") int page) {
        return data.requests(actor, Audience.STUDENT, null, page);
    }

    @Operation(summary = "Lớp học phần chọn được khi gửi đơn nghỉ")
    @GetMapping("/requests/leave-options")
    public LeaveFormOptions leaveOptions(@AuthenticationPrincipal AppUserPrincipal actor) {
        return data.leaveOptions(actor, Audience.STUDENT, null);
    }

    @Operation(summary = "Gửi đơn nghỉ (multipart/form-data; ngày yyyy-MM-dd hoặc dd/MM/yyyy; files tùy chọn)")
    @PostMapping(value = "/requests/leave", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetail createLeave(@AuthenticationPrincipal AppUserPrincipal actor,
                                     @Valid @ModelAttribute LeaveRequestForm form,
                                     @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                     HttpServletRequest request) throws IOException {
        return data.createLeave(actor, Audience.STUDENT, null, form, files, request.getRemoteAddr());
    }

    @Operation(summary = "Chi tiết đơn")
    @GetMapping("/requests/{id}")
    public RequestDetail request(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id) {
        return data.request(actor, Audience.STUDENT, null, id);
    }

    @Operation(summary = "Hủy đơn đang chờ duyệt")
    @PostMapping("/requests/{id}/cancel")
    public RequestDetail cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                HttpServletRequest request) {
        return data.cancel(actor, Audience.STUDENT, null, id, request.getRemoteAddr());
    }
}
