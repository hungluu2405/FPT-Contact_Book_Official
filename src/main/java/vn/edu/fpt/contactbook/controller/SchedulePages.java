package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ScheduleViewService;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.time.LocalDate;

/**
 * Dựng model chung cho trang thời khóa biểu và lịch thi của giảng viên, sinh viên, phụ huynh
 * (templates common/schedule.html, common/exams.html). Phạm vi dữ liệu kiểm tra ở ScheduleViewService.
 */
@Component
@RequiredArgsConstructor
class SchedulePages {

    static final String WEEK = "week";
    static final String LIST = "list";

    private final ScheduleViewService scheduleViewService;
    private final CatalogLookupService catalogLookupService;

    /** view: "week" (mặc định) hoặc "list"; week: một ngày bất kỳ trong tuần cần xem. */
    String schedule(Model model, AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId,
                    String view, LocalDate week, int page, String baseUrl, String examsUrl, String title) {
        String mode = LIST.equals(view) ? LIST : WEEK;
        SemesterView semester;
        if (WEEK.equals(mode)) {
            InSemester<ScheduleWeek> result = scheduleViewService.week(actor, audience, studentId, semesterId, week);
            semester = result.semester();
            model.addAttribute("week", result.data());
        } else {
            InSemester<Page<ScheduleItem>> result = scheduleViewService.sessions(actor, audience, studentId,
                    semesterId, page);
            semester = result.semester();
            model.addAttribute("items", result.data());
            model.addAttribute("pager", PageUtils.pager(result.data(), baseUrl,
                    PageUtils.params("semesterId", semester.id(), "view", LIST)));
        }
        common(model, semester, baseUrl, title);
        model.addAttribute("view", mode);
        model.addAttribute("examsUrl", examsUrl);
        model.addAttribute("today", AppTime.today());
        return "common/schedule";
    }

    String exams(Model model, AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId, int page,
                 String baseUrl, String scheduleUrl, String title) {
        InSemester<Page<ExamView>> result = scheduleViewService.exams(actor, audience, studentId, semesterId, page);
        SemesterView semester = result.semester();
        model.addAttribute("items", result.data());
        model.addAttribute("pager", PageUtils.pager(result.data(), baseUrl,
                PageUtils.params("semesterId", semester.id())));
        common(model, semester, baseUrl, title);
        model.addAttribute("scheduleUrl", scheduleUrl);
        return "common/exams";
    }

    private void common(Model model, SemesterView semester, String baseUrl, String title) {
        model.addAttribute("semester", semester);
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("pageTitle", title);
    }
}
