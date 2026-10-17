package vn.edu.fpt.contactbook.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Đổi lỗi chưa được controller xử lý thành thông báo thân thiện, không lộ chi tiết kỹ thuật.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // Tệp vượt spring.servlet.multipart.max-request-size bị Tomcat trả 413 trước khi tới Spring MVC,
    // nên không bắt ở đây được; trang thân thiện nằm ở templates/error/413.html.

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/";
    }

    /** Vi phạm ràng buộc database (UNIQUE, CHECK...) lọt qua kiểm tra ở service. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String dataIntegrity(DataIntegrityViolationException e, RedirectAttributes redirectAttributes) {
        log.warn("Data integrity violation", e);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Dữ liệu không hợp lệ hoặc bị trùng, vui lòng kiểm tra lại");
        return "redirect:/";
    }
}
