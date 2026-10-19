package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.StudentGradeRow;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.GradeViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.util.List;

/**
 * Điểm đã công bố của sinh viên theo học kỳ (chỉ của chính mình; lọc công bố ở GradeViewService).
 */
@Controller
@RequiredArgsConstructor
public class StudentGradeController {

    private final GradeViewService gradeViewService;
    private final CatalogLookupService catalogLookupService;

    @GetMapping("/student/grades")
    public String grades(@AuthenticationPrincipal AppUserPrincipal actor,
                         @RequestParam(required = false) Long semesterId, Model model) {
        return page(model, gradeViewService.student(actor, Audience.STUDENT, null, semesterId), "/student/grades",
                "Điểm", "student-grades", catalogLookupService);
    }

    /** Model chung của trang điểm sinh viên và phụ huynh (common/grades.html). */
    static String page(Model model, InSemester<List<StudentGradeRow>> result, String baseUrl, String title,
                       String menu, CatalogLookupService catalogLookupService) {
        model.addAttribute("semester", result.semester());
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("items", result.data());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("pageTitle", title);
        model.addAttribute("activeMenu", menu);
        return "common/grades";
    }
}
