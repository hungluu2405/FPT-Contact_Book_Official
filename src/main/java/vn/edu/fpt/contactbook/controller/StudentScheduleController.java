package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.time.LocalDate;

/**
 * Thời khóa biểu và lịch thi của sinh viên (chỉ các lớp học phần mình đang học).
 */
@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentScheduleController {

    private final SchedulePages schedulePages;

    @GetMapping("/schedule")
    public String schedule(@AuthenticationPrincipal AppUserPrincipal actor,
                           @RequestParam(required = false) Long semesterId,
                           @RequestParam(required = false) String view,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeMenu", "student-schedule");
        return schedulePages.schedule(model, actor, Audience.STUDENT, null, semesterId, view, week, page,
                "/student/schedule", "/student/exams", "Thời khóa biểu");
    }

    @GetMapping("/exams")
    public String exams(@AuthenticationPrincipal AppUserPrincipal actor,
                        @RequestParam(required = false) Long semesterId,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeMenu", "student-exams");
        return schedulePages.exams(model, actor, Audience.STUDENT, null, semesterId, page, "/student/exams",
                "/student/schedule", "Lịch thi");
    }
}
