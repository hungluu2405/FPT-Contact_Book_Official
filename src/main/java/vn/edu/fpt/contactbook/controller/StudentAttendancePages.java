package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceViewService;
import vn.edu.fpt.contactbook.service.AttendanceViewService.StudentHistory;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.util.List;

/**
 * Dựng model chung cho trang điểm danh của sinh viên và của con (common/attendance.html,
 * common/attendance-history.html). baseUrl: /student/attendance hoặc /parent/children/{id}/attendance.
 */
@Component
@RequiredArgsConstructor
class StudentAttendancePages {

    private final AttendanceViewService attendanceViewService;
    private final CatalogLookupService catalogLookupService;

    String overview(Model model, AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId,
                    String baseUrl, String title) {
        InSemester<List<StudentAttendanceRow>> result = attendanceViewService.student(actor, audience, studentId,
                semesterId);
        model.addAttribute("semester", result.semester());
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("items", result.data());
        model.addAttribute("threshold", attendanceViewService.thresholdPercent());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("pageTitle", title);
        return "common/attendance";
    }

    String history(Model model, AppUserPrincipal actor, Audience audience, Long studentId, Long sectionId, int page,
                   String baseUrl) {
        StudentHistory history = attendanceViewService.history(actor, audience, studentId, sectionId, page);
        model.addAttribute("summary", history.summary());
        model.addAttribute("items", history.sessions());
        model.addAttribute("pager", PageUtils.pager(history.sessions(), baseUrl + "/" + sectionId,
                PageUtils.params()));
        model.addAttribute("threshold", attendanceViewService.thresholdPercent());
        model.addAttribute("baseUrl", baseUrl);
        return "common/attendance-history";
    }
}
