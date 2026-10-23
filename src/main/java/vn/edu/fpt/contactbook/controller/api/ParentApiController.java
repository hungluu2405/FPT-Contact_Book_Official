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
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.dto.RequestListItem;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.StudentGradeRow;
import vn.edu.fpt.contactbook.dto.api.AttendanceHistoryResponse;
import vn.edu.fpt.contactbook.dto.api.AttendanceOverview;
import vn.edu.fpt.contactbook.dto.api.PageResponse;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu của con đã liên kết (PARENT, ApiSecurityConfig); studentId là students.id. Con chưa liên kết → 403, kiểm
 * tra ở service. semesterId bỏ trống: học kỳ hiện tại.
 */
@Tag(name = "Parent", description = "Phụ huynh xem dữ liệu của con, gửi và hủy đơn nghỉ thay con")
@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
public class ParentApiController {

    private final StudentDataApi data;
    private final ScheduleViewService scheduleViewService;

    @Operation(summary = "Các con đã liên kết (id là students.id)")
    @GetMapping("/children")
    public List<OptionView> children(@AuthenticationPrincipal AppUserPrincipal actor) {
        return scheduleViewService.children(actor);
    }

    @Operation(summary = "Thời khóa biểu một tuần của con (date: ngày bất kỳ trong tuần, yyyy-MM-dd)")
    @GetMapping("/children/{studentId}/schedule/week")
    public InSemester<ScheduleWeek> week(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @PathVariable Long studentId,
                                         @RequestParam(required = false) Long semesterId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return data.week(actor, Audience.PARENT, studentId, semesterId, date);
    }

    @Operation(summary = "Danh sách buổi học của con trong học kỳ")
    @GetMapping("/children/{studentId}/schedule/sessions")
    public InSemester<PageResponse<ScheduleItem>> sessions(@AuthenticationPrincipal AppUserPrincipal actor,
                                                           @PathVariable Long studentId,
                                                           @RequestParam(required = false) Long semesterId,
                                                           @RequestParam(defaultValue = "0") int page) {
        return data.sessions(actor, Audience.PARENT, studentId, semesterId, page);
    }

    @Operation(summary = "Lịch thi của con")
    @GetMapping("/children/{studentId}/exams")
    public InSemester<PageResponse<ExamView>> exams(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @PathVariable Long studentId,
                                                    @RequestParam(required = false) Long semesterId,
                                                    @RequestParam(defaultValue = "0") int page) {
        return data.exams(actor, Audience.PARENT, studentId, semesterId, page);
    }

    @Operation(summary = "Điểm của con (chỉ đầu điểm đã công bố)")
    @GetMapping("/children/{studentId}/grades")
    public InSemester<List<StudentGradeRow>> grades(@AuthenticationPrincipal AppUserPrincipal actor,
                                                    @PathVariable Long studentId,
                                                    @RequestParam(required = false) Long semesterId) {
        return data.grades(actor, Audience.PARENT, studentId, semesterId);
    }

    @Operation(summary = "Chuyên cần của con theo môn")
    @GetMapping("/children/{studentId}/attendance")
    public AttendanceOverview attendance(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @PathVariable Long studentId,
                                         @RequestParam(required = false) Long semesterId) {
        return data.attendance(actor, Audience.PARENT, studentId, semesterId);
    }

    @Operation(summary = "Điểm danh từng buổi của con ở một lớp môn học")
    @GetMapping("/children/{studentId}/attendance/{sectionId}")
    public AttendanceHistoryResponse attendanceHistory(@AuthenticationPrincipal AppUserPrincipal actor,
                                                       @PathVariable Long studentId, @PathVariable Long sectionId,
                                                       @RequestParam(defaultValue = "0") int page) {
        return data.attendanceHistory(actor, Audience.PARENT, studentId, sectionId, page);
    }

    @Operation(summary = "Danh sách đơn của con (mọi loại đơn)")
    @GetMapping("/children/{studentId}/requests")
    public PageResponse<RequestListItem> requests(@AuthenticationPrincipal AppUserPrincipal actor,
                                                  @PathVariable Long studentId,
                                                  @RequestParam(defaultValue = "0") int page) {
        return data.requests(actor, Audience.PARENT, studentId, page);
    }

    @Operation(summary = "Lớp môn học chọn được khi gửi đơn nghỉ thay con")
    @GetMapping("/children/{studentId}/requests/leave-options")
    public LeaveFormOptions leaveOptions(@AuthenticationPrincipal AppUserPrincipal actor,
                                         @PathVariable Long studentId) {
        return data.leaveOptions(actor, Audience.PARENT, studentId);
    }

    @Operation(summary = "Gửi đơn nghỉ thay con (multipart/form-data; ngày yyyy-MM-dd hoặc dd/MM/yyyy)")
    @PostMapping(value = "/children/{studentId}/requests/leave", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetail createLeave(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                                     @Valid @ModelAttribute LeaveRequestForm form,
                                     @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                     HttpServletRequest request) throws IOException {
        return data.createLeave(actor, Audience.PARENT, studentId, form, files, request.getRemoteAddr());
    }

    @Operation(summary = "Chi tiết đơn của con")
    @GetMapping("/children/{studentId}/requests/{id}")
    public RequestDetail request(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                                 @PathVariable Long id) {
        return data.request(actor, Audience.PARENT, studentId, id);
    }

    @Operation(summary = "Hủy đơn đang chờ duyệt (đơn do chính phụ huynh gửi)")
    @PostMapping("/children/{studentId}/requests/{id}/cancel")
    public RequestDetail cancel(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                                @PathVariable Long id, HttpServletRequest request) {
        return data.cancel(actor, Audience.PARENT, studentId, id, request.getRemoteAddr());
    }
}
