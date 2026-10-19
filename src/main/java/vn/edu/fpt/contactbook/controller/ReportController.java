package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.dto.AttendanceReport;
import vn.edu.fpt.contactbook.dto.ClubReportRow;
import vn.edu.fpt.contactbook.dto.GradeReport;
import vn.edu.fpt.contactbook.dto.GradeStats;
import vn.edu.fpt.contactbook.dto.ReportFile;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.dto.ReportSelection;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceReportService;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.ClubReportService;
import vn.edu.fpt.contactbook.service.GradeReportService;
import vn.edu.fpt.contactbook.service.UserImportWorkbooks;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.util.Map;

/**
 * Báo cáo (SRS FR-REPORT-01..04): điểm danh, điểm (GV, TBM, ADMIN; phạm vi lớp/bộ môn kiểm tra ở service, kể cả khi
 * sửa tham số trên URL) và CLB (chỉ ADMIN). Chỉ có GET; xuất Excel trả file và ghi audit EXPORT_REPORT ở service.
 */
@Controller
@RequestMapping(ReportController.BASE)
@RequiredArgsConstructor
public class ReportController {

    static final String BASE = "/reports";
    private static final String MENU = "reports";

    private final AttendanceReportService attendanceReportService;
    private final GradeReportService gradeReportService;
    private final ClubReportService clubReportService;
    private final CatalogLookupService catalogLookupService;
    private final ReportCharts reportCharts;

    @GetMapping
    public String index() {
        return "redirect:" + BASE + "/attendance";
    }

    @GetMapping("/attendance")
    public String attendance(@AuthenticationPrincipal AppUserPrincipal actor,
                             @RequestParam(required = false) Long semesterId,
                             @RequestParam(required = false) Long subjectId,
                             @RequestParam(required = false) Long sectionId,
                             @RequestParam(defaultValue = "0") int page, Model model) {
        AttendanceReport report = attendanceReportService.report(actor,
                new ReportFilter(semesterId, subjectId, sectionId), page);
        filters(model, report.selection(), BASE + "/attendance");
        model.addAttribute("report", report);
        model.addAttribute("pager", PageUtils.pager(report.rows(), BASE + "/attendance", params(report.selection())));
        model.addAttribute("chart", reportCharts.attendance(report));
        return "common/report-attendance";
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(@AuthenticationPrincipal AppUserPrincipal actor,
                                                   @RequestParam(required = false) Long semesterId,
                                                   @RequestParam(required = false) Long subjectId,
                                                   @RequestParam(required = false) Long sectionId,
                                                   HttpServletRequest request) {
        return download(attendanceReportService.export(actor, new ReportFilter(semesterId, subjectId, sectionId),
                request.getRemoteAddr()));
    }

    @GetMapping("/grades")
    public String grades(@AuthenticationPrincipal AppUserPrincipal actor,
                         @RequestParam(required = false) Long semesterId,
                         @RequestParam(required = false) Long subjectId,
                         @RequestParam(required = false) Long sectionId,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        GradeReport report = gradeReportService.report(actor, new ReportFilter(semesterId, subjectId, sectionId),
                page);
        filters(model, report.selection(), BASE + "/grades");
        model.addAttribute("report", report);
        model.addAttribute("pager", PageUtils.pager(report.rows(), BASE + "/grades", params(report.selection())));
        model.addAttribute("bands", GradeStats.BANDS);
        model.addAttribute("chart", reportCharts.grades(report));
        return "common/report-grades";
    }

    @GetMapping("/grades/export")
    public ResponseEntity<byte[]> exportGrades(@AuthenticationPrincipal AppUserPrincipal actor,
                                               @RequestParam(required = false) Long semesterId,
                                               @RequestParam(required = false) Long subjectId,
                                               @RequestParam(required = false) Long sectionId,
                                               HttpServletRequest request) {
        return download(gradeReportService.export(actor, new ReportFilter(semesterId, subjectId, sectionId),
                request.getRemoteAddr()));
    }

    @GetMapping("/clubs")
    public String clubs(@AuthenticationPrincipal AppUserPrincipal actor,
                        @RequestParam(required = false) ClubStatus status,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        Page<ClubReportRow> rows = clubReportService.report(actor, status, page);
        model.addAttribute("rows", rows);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ClubStatus.values());
        model.addAttribute("pager", PageUtils.pager(rows, BASE + "/clubs", PageUtils.params("status", status)));
        model.addAttribute("chart", reportCharts.clubs(rows));
        model.addAttribute("activeMenu", MENU);
        return "common/report-clubs";
    }

    private void filters(Model model, ReportSelection selection, String path) {
        model.addAttribute("selection", selection);
        model.addAttribute("semesters", catalogLookupService.semesters());
        model.addAttribute("path", path);
        model.addAttribute("activeMenu", MENU);
    }

    /** Bộ lọc đã áp dụng (học kỳ đã giải, môn) để giữ khi chuyển trang; trang luôn ở chế độ danh sách lớp. */
    private static Map<String, Object> params(ReportSelection selection) {
        return PageUtils.params("semesterId", selection.semester().id(), "subjectId", selection.subjectId());
    }

    private static ResponseEntity<byte[]> download(ReportFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(UserImportWorkbooks.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
