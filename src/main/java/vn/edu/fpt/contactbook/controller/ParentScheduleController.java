package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.time.LocalDate;
import java.util.List;

/**
 * Thời khóa biểu và lịch thi của con (studentId là students.id). Con chưa liên kết → 403, kiểm tra ở
 * ScheduleViewService. /parent/schedule và /parent/exams chuyển sang con đầu tiên đã liên kết.
 */
@Controller
@RequestMapping("/parent")
@RequiredArgsConstructor
public class ParentScheduleController {

    private final SchedulePages schedulePages;
    private final ScheduleViewService scheduleViewService;

    @GetMapping("/schedule")
    public String firstChildSchedule(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        return firstChild(actor, model, "schedule");
    }

    @GetMapping("/exams")
    public String firstChildExams(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        return firstChild(actor, model, "exams");
    }

    @GetMapping("/children/{studentId:\\d+}/schedule")
    public String schedule(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                           @RequestParam(required = false) Long semesterId,
                           @RequestParam(required = false) String view,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        String template = schedulePages.schedule(model, actor, Audience.PARENT, studentId, semesterId, view, week,
                page, base(studentId) + "/schedule", base(studentId) + "/exams", "Thời khóa biểu của con");
        children(actor, studentId, model, "schedule");
        model.addAttribute("activeMenu", "parent-schedule");
        return template;
    }

    @GetMapping("/children/{studentId:\\d+}/exams")
    public String exams(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                        @RequestParam(required = false) Long semesterId,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        String template = schedulePages.exams(model, actor, Audience.PARENT, studentId, semesterId, page,
                base(studentId) + "/exams", base(studentId) + "/schedule", "Lịch thi của con");
        children(actor, studentId, model, "exams");
        model.addAttribute("activeMenu", "parent-exams");
        return template;
    }

    /** Chưa liên kết con nào thì hiện trang trống kèm hướng dẫn. */
    private String firstChild(AppUserPrincipal actor, Model model, String page) {
        List<OptionView> children = scheduleViewService.children(actor);
        if (children.isEmpty()) {
            model.addAttribute("noChildren", true);
            model.addAttribute("pageTitle", "schedule".equals(page) ? "Thời khóa biểu của con" : "Lịch thi của con");
            model.addAttribute("activeMenu", "parent-" + page);
            return "schedule".equals(page) ? "common/schedule" : "common/exams";
        }
        return "redirect:" + base(children.getFirst().id()) + "/" + page;
    }

    private void children(AppUserPrincipal actor, Long studentId, Model model, String page) {
        model.addAttribute("children", scheduleViewService.children(actor));
        model.addAttribute("studentId", studentId);
        model.addAttribute("childUrlTemplate", "/parent/children/{id}/" + page);
    }

    private static String base(Long studentId) {
        return "/parent/children/" + studentId;
    }
}
