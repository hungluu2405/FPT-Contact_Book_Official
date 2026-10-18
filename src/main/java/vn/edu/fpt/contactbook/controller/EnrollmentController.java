package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.EnrollClassResult;
import vn.edu.fpt.contactbook.dto.EnrollmentImportResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ClassSectionService;
import vn.edu.fpt.contactbook.service.EnrollmentImportService;
import vn.edu.fpt.contactbook.service.EnrollmentService;

import java.io.IOException;

/**
 * Ghi danh sinh viên vào lớp học phần (SRS FR-ORG-06): chỉ ADMIN. Trưởng bộ môn xem được lớp nhưng mọi
 * URL ở đây đều trả 403 với họ.
 */
@Controller
@RequestMapping(ClassSectionController.BASE + "/{id:\\d+}/enrollments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final EnrollmentImportService enrollmentImportService;
    private final ClassSectionService classSectionService;

    @PostMapping
    public String enroll(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam Long studentId, @RequestParam(required = false) String q,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            boolean reEnrolled = enrollmentService.enroll(actor, id, studentId, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage",
                    reEnrolled ? "Đã ghi danh lại sinh viên" : "Đã ghi danh sinh viên");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // Giữ ô tìm kiếm để ghi danh tiếp nhiều sinh viên.
        if (q != null && !q.isBlank()) {
            redirectAttributes.addAttribute("q", q);
        }
        return "redirect:" + detail(id);
    }

    @PostMapping("/{studentId:\\d+}/drop")
    public String drop(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @PathVariable Long studentId, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(detail(id), redirectAttributes, "Đã gỡ sinh viên khỏi lớp",
                () -> enrollmentService.drop(actor, id, studentId, request.getRemoteAddr()));
    }

    @PostMapping("/class")
    public String enrollClass(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                              @RequestParam(required = false) Long classId, HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            EnrollClassResult result = enrollmentService.enrollClass(actor, id, classId, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi danh " + result.added()
                    + " sinh viên mới, ghi danh lại " + result.reEnrolled() + "; bỏ qua " + result.skippedEnrolled()
                    + " sinh viên đã có trong lớp và " + result.skippedNotStudying() + " sinh viên không đang học");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + detail(id);
    }

    @GetMapping("/import")
    public String importPage(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        return importView(actor, id, model);
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EnrollmentImportService.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("mau-ghi-danh.xlsx").build().toString())
                .body(enrollmentImportService.template());
    }

    /** Có lỗi: hiện lại trang với thông báo hoặc bảng lỗi theo dòng. Thành công: về trang lớp. */
    @PostMapping("/import")
    public String upload(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(value = "file", required = false) MultipartFile file, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        if (file == null || file.isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng chọn file Excel để tải lên");
            return importView(actor, id, model);
        }
        EnrollmentImportResult result;
        try {
            result = enrollmentImportService.importFile(actor, id, file.getOriginalFilename(), file.getBytes(),
                    request.getRemoteAddr());
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return importView(actor, id, model);
        }
        if (!result.success()) {
            model.addAttribute("importErrors", result.errors());
            return importView(actor, id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã ghi danh " + result.added()
                + " sinh viên mới và ghi danh lại " + result.reEnrolled() + " sinh viên từ file Excel");
        return "redirect:" + detail(id);
    }

    private String importView(AppUserPrincipal actor, Long id, Model model) {
        model.addAttribute("item", classSectionService.detail(actor, id));
        model.addAttribute("maxRows", EnrollmentImportService.MAX_ROWS);
        model.addAttribute("activeMenu", ClassSectionController.MENU);
        return "admin/sections/import";
    }

    private static String detail(Long id) {
        return ClassSectionController.BASE + "/" + id;
    }
}
