package vn.edu.fpt.contactbook.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Đổi lỗi chưa được controller xử lý thành thông báo thân thiện, không lộ chi tiết kỹ thuật.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** Tệp vượt spring.servlet.multipart.max-file-size; hiện chỉ trang hồ sơ có tải tệp. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Tệp tải lên vượt quá dung lượng cho phép");
        return "redirect:/profile";
    }

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
