package vn.edu.fpt.contactbook.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.contactbook.controller.Uploads;
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
import vn.edu.fpt.contactbook.service.AttendanceViewService;
import vn.edu.fpt.contactbook.service.AttendanceViewService.StudentHistory;
import vn.edu.fpt.contactbook.service.GradeViewService;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Phần chung của API sinh viên (Audience.STUDENT, studentId bị bỏ qua) và phụ huynh (Audience.PARENT, studentId là
 * con). Chỉ gọi service của web: phạm vi dữ liệu kiểm tra ở service, ngoài phạm vi → AccessDeniedException (403).
 */
@Component
@RequiredArgsConstructor
class StudentDataApi {

    private final ScheduleViewService scheduleViewService;
    private final GradeViewService gradeViewService;
    private final AttendanceViewService attendanceViewService;
    private final RequestService requestService;

    InSemester<ScheduleWeek> week(AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId,
                                  LocalDate date) {
        return scheduleViewService.week(actor, audience, studentId, semesterId, date);
    }

    InSemester<PageResponse<ScheduleItem>> sessions(AppUserPrincipal actor, Audience audience, Long studentId,
                                                    Long semesterId, int page) {
        return paged(scheduleViewService.sessions(actor, audience, studentId, semesterId, page));
    }

    InSemester<PageResponse<ExamView>> exams(AppUserPrincipal actor, Audience audience, Long studentId,
                                             Long semesterId, int page) {
        return paged(scheduleViewService.exams(actor, audience, studentId, semesterId, page));
    }

    InSemester<List<StudentGradeRow>> grades(AppUserPrincipal actor, Audience audience, Long studentId,
                                             Long semesterId) {
        return gradeViewService.student(actor, audience, studentId, semesterId);
    }

    AttendanceOverview attendance(AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId) {
        var result = attendanceViewService.student(actor, audience, studentId, semesterId);
        return new AttendanceOverview(result.semester(), attendanceViewService.thresholdPercent(), result.data());
    }

    AttendanceHistoryResponse attendanceHistory(AppUserPrincipal actor, Audience audience, Long studentId,
                                                Long sectionId, int page) {
        StudentHistory history = attendanceViewService.history(actor, audience, studentId, sectionId, page);
        return new AttendanceHistoryResponse(history.summary(), attendanceViewService.thresholdPercent(),
                PageResponse.from(history.sessions()));
    }

    PageResponse<RequestListItem> requests(AppUserPrincipal actor, Audience audience, Long studentId, int page) {
        return PageResponse.from(requestService.studentRequests(actor, view(audience), studentId, page));
    }

    RequestDetail request(AppUserPrincipal actor, Audience audience, Long studentId, Long requestId) {
        return requestService.detail(actor, view(audience), studentId, requestId);
    }

    LeaveFormOptions leaveOptions(AppUserPrincipal actor, Audience audience, Long studentId) {
        return requestService.leaveOptions(actor, view(audience), studentId);
    }

    /** Gửi đơn nghỉ rồi trả chi tiết đơn vừa tạo. */
    RequestDetail createLeave(AppUserPrincipal actor, Audience audience, Long studentId, LeaveRequestForm form,
                              List<MultipartFile> files, String ipAddress) throws IOException {
        Long id = requestService.createLeave(actor, view(audience), studentId, form, Uploads.of(files), ipAddress);
        return requestService.detail(actor, view(audience), studentId, id);
    }

    /** Hủy đơn còn chờ duyệt (người gửi đơn); trả chi tiết sau khi hủy. */
    RequestDetail cancel(AppUserPrincipal actor, Audience audience, Long studentId, Long requestId,
                         String ipAddress) {
        requestService.detail(actor, view(audience), studentId, requestId);
        requestService.cancel(actor, requestId, ipAddress);
        return requestService.detail(actor, view(audience), studentId, requestId);
    }

    private static <T> InSemester<PageResponse<T>> paged(InSemester<Page<T>> result) {
        return new InSemester<>(result.semester(), PageResponse.from(result.data()));
    }

    private static RequestService.View view(Audience audience) {
        return audience == Audience.PARENT ? RequestService.View.PARENT : RequestService.View.STUDENT;
    }
}
