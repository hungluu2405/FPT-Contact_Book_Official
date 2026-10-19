package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

/**
 * Điểm danh của sinh viên theo môn và lịch sử từng buổi (chỉ dữ liệu của chính mình, kiểm tra ở service).
 */
@Controller
@RequestMapping("/student/attendance")
@RequiredArgsConstructor
public class StudentAttendanceController {

    private static final String BASE = "/student/attendance";

    private final StudentAttendancePages pages;

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserPrincipal actor,
                           @RequestParam(required = false) Long semesterId, Model model) {
        model.addAttribute("activeMenu", "student-attendance");
        return pages.overview(model, actor, Audience.STUDENT, null, semesterId, BASE, "Điểm danh");
    }

    @GetMapping("/{sectionId:\\d+}")
    public String history(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long sectionId,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeMenu", "student-attendance");
        return pages.history(model, actor, Audience.STUDENT, null, sectionId, page, BASE);
    }
}
