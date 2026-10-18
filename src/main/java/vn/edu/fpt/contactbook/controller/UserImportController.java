package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.fpt.contactbook.dto.UserImportResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.UserImportService;
import vn.edu.fpt.contactbook.service.UserImportType;
import vn.edu.fpt.contactbook.service.UserImportWorkbooks;
import vn.edu.fpt.contactbook.util.AppTime;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Nhập người dùng từ Excel (chỉ ADMIN): trang nhập, tải file mẫu, tải file lên.
 * Nhập thành công thì trả file kết quả (có mật khẩu tạm) ngay trong phản hồi, không lưu trên server.
 */
@Controller
@RequestMapping("/admin/users/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserImportController {

    private static final String VIEW = "admin/users/import";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final UserImportService userImportService;
    private final UserImportWorkbooks userImportWorkbooks;

    @GetMapping
    public ModelAndView page(@RequestParam(defaultValue = "STUDENT") UserImportType type) {
        return view(type);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam UserImportType type) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(UserImportWorkbooks.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("mau-nhap-" + type.slug() + ".xlsx").build().toString())
                .body(userImportWorkbooks.template(type));
    }

    /**
     * Có lỗi: hiện lại trang với bảng lỗi. Thành công: ghi file kết quả vào phản hồi và trả null
     * (Spring coi yêu cầu đã được xử lý xong).
     */
    @PostMapping
    public ModelAndView upload(@AuthenticationPrincipal AppUserPrincipal actor,
                               @RequestParam UserImportType type,
                               @RequestParam(value = "file", required = false) MultipartFile file,
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        ModelAndView page = view(type);
        if (file == null || file.isEmpty()) {
            page.addObject("errorMessage", "Vui lòng chọn file Excel để tải lên");
            return page;
        }
        UserImportResult result;
        try {
            result = userImportService.importUsers(actor, type, file.getOriginalFilename(), file.getBytes(),
                    request.getRemoteAddr());
        } catch (BusinessException e) {
            page.addObject("errorMessage", e.getMessage());
            return page;
        }
        if (!result.success()) {
            page.addObject("importErrors", result.errors());
            return page;
        }

        byte[] content = userImportWorkbooks.result(type, result.created());
        response.setContentType(UserImportWorkbooks.CONTENT_TYPE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("ket-qua-nhap-" + type.slug() + "-" + AppTime.now().format(FILE_TIME) + ".xlsx")
                .build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        return null;
    }

    private static ModelAndView view(UserImportType type) {
        ModelAndView page = new ModelAndView(VIEW);
        page.addObject("type", type);
        page.addObject("types", UserImportType.values());
        page.addObject("maxRows", UserImportService.MAX_ROWS);
        page.addObject("activeMenu", "users");
        return page;
    }
}
