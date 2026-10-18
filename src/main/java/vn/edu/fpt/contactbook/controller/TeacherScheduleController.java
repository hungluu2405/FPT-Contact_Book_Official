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
 * Lịch dạy và lịch thi của giảng viên (chỉ các lớp học phần mình dạy). Trưởng bộ môn cũng dùng trang này.
 */
@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherScheduleController {

    private final SchedulePages schedulePages;

    @GetMapping("/schedule")
    public String schedule(@AuthenticationPrincipal AppUserPrincipal actor,
                           @RequestParam(required = false) Long semesterId,
                           @RequestParam(required = false) String view,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeMenu", "teacher-schedule");
        return schedulePages.schedule(model, actor, Audience.TEACHER, null, semesterId, view, week, page,
                "/teacher/schedule", "/teacher/exams", "Lịch dạy");
    }

    @GetMapping("/exams")
    public String exams(@AuthenticationPrincipal AppUserPrincipal actor,
                        @RequestParam(required = false) Long semesterId,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeMenu", "teacher-exams");
        return schedulePages.exams(model, actor, Audience.TEACHER, null, semesterId, page, "/teacher/exams",
                "/teacher/schedule", "Lịch thi các lớp dạy");
    }
}
