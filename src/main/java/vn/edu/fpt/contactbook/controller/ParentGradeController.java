package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.GradeViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.util.List;

/**
 * Điểm đã công bố của con (studentId là students.id). Con chưa liên kết → 403, kiểm tra ở service.
 * /parent/grades chuyển sang con đầu tiên đã liên kết; chưa liên kết con nào thì hiện trang trống kèm hướng dẫn.
 */
@Controller
@RequestMapping("/parent")
@RequiredArgsConstructor
public class ParentGradeController {

    private static final String MENU = "parent-grades";
    private static final String TITLE = "Điểm của con";

    private final GradeViewService gradeViewService;
    private final ScheduleViewService scheduleViewService;
    private final CatalogLookupService catalogLookupService;

    @GetMapping("/grades")
    public String firstChild(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        List<OptionView> children = scheduleViewService.children(actor);
        if (children.isEmpty()) {
            model.addAttribute("noChildren", true);
            model.addAttribute("pageTitle", TITLE);
            model.addAttribute("activeMenu", MENU);
            return "common/grades";
        }
        return "redirect:" + base(children.getFirst().id());
    }

    @GetMapping("/children/{studentId:\\d+}/grades")
    public String grades(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long studentId,
                         @RequestParam(required = false) Long semesterId, Model model) {
        String template = StudentGradeController.page(model,
                gradeViewService.student(actor, Audience.PARENT, studentId, semesterId), base(studentId), TITLE,
                MENU, catalogLookupService);
        model.addAttribute("children", scheduleViewService.children(actor));
        model.addAttribute("studentId", studentId);
        model.addAttribute("childUrlTemplate", "/parent/children/{id}/grades");
        return template;
    }

    private static String base(Long studentId) {
        return "/parent/children/" + studentId + "/grades";
    }
}
