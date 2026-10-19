package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.GradeEntryForm;
import vn.edu.fpt.contactbook.dto.GradeImportResult;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.GradeImportService;
import vn.edu.fpt.contactbook.service.GradeService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nhập điểm của giảng viên (SRS FR-GRADE-02, 03): chỉ lớp mình dạy (kiểm tra ở service, kể cả gõ URL), chỉ ô chưa
 * công bố. Lỗi theo ô hiện cạnh từng ô; có lỗi thì không lưu ô nào.
 */
@Controller
@RequestMapping(TeacherAttendanceController.BASE + "/{id:\\d+}/grades")
@RequiredArgsConstructor
public class TeacherGradeController {

    private static final String MENU = "teacher-sections";

    private final GradeService gradeService;
    private final GradeImportService gradeImportService;

    @GetMapping
    public String sheet(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        GradeSheet sheet = gradeService.sheet(actor, Access.TEACHER, id);
        GradeEntryForm form = new GradeEntryForm();
        sheet.rows().forEach(row -> row.cells().stream()
                .filter(cell -> !cell.published() && cell.score() != null)
                .forEach(cell -> form.getScores().put(cell.key(), cell.score().stripTrailingZeros().toPlainString())));
        model.addAttribute("form", form);
        return sheetView(model, sheet, Map.of());
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @ModelAttribute("form") GradeEntryForm form, Model model, HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        try {
            int changed = gradeService.save(actor, id, form, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage",
                    changed == 0 ? "Không có thay đổi nào để lưu" : "Đã lưu " + changed + " ô điểm");
            return "redirect:" + base(id);
        } catch (BusinessException e) {
            // Lỗi theo ô: khóa "scores[key]" → key; lỗi chung (ô đã công bố, danh sách đổi) hiện đầu trang.
            Map<String, String> cellErrors = new LinkedHashMap<>();
            e.getFieldErrors().forEach((field, message) ->
                    cellErrors.put(field.substring("scores[".length(), field.length() - 1), message));
            model.addAttribute("errorMessage", cellErrors.isEmpty() ? e.getMessage()
                    : "Có ô điểm không hợp lệ, chưa lưu ô nào. Hãy sửa các ô được đánh dấu");
            return sheetView(model, gradeService.sheet(actor, Access.TEACHER, id), cellErrors);
        }
    }

    @GetMapping("/import")
    public String importPage(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        return importView(actor, id, model);
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id) {
        byte[] content = gradeImportService.template(actor, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GradeImportService.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("mau-nhap-diem.xlsx").build().toString())
                .body(content);
    }

    /** Có lỗi: hiện lại trang với thông báo hoặc bảng lỗi theo dòng. Thành công: về bảng điểm. */
    @PostMapping("/import")
    public String upload(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(value = "file", required = false) MultipartFile file, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        if (file == null || file.isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng chọn file Excel để tải lên");
            return importView(actor, id, model);
        }
        GradeImportResult result;
        try {
            result = gradeImportService.importFile(actor, id, file.getOriginalFilename(), file.getBytes(),
                    request.getRemoteAddr());
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return importView(actor, id, model);
        }
        if (!result.success()) {
            model.addAttribute("importErrors", result.errors());
            return importView(actor, id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã nhập " + result.changed()
                + " ô điểm từ file Excel");
        return "redirect:" + base(id);
    }

    private String sheetView(Model model, GradeSheet sheet, Map<String, String> cellErrors) {
        model.addAttribute("sheet", sheet);
        model.addAttribute("cellErrors", cellErrors);
        model.addAttribute("activeMenu", MENU);
        return "teacher/grades";
    }

    private String importView(AppUserPrincipal actor, Long id, Model model) {
        model.addAttribute("sheet", gradeService.sheet(actor, Access.TEACHER, id));
        model.addAttribute("maxRows", GradeImportService.MAX_ROWS);
        model.addAttribute("activeMenu", MENU);
        return "teacher/grades-import";
    }

    private static String base(Long id) {
        return TeacherAttendanceController.BASE + "/" + id + "/grades";
    }
}
