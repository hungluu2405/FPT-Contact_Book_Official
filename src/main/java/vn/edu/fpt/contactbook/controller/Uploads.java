package vn.edu.fpt.contactbook.controller;

import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Đổi tệp tải lên (multipart) thành AttachmentUpload cho service; dùng chung cho form web (thông báo, đơn từ) và
 * REST API. Bỏ phần tệp rỗng trình duyệt gửi khi không chọn tệp; tệp còn lại kiểm tra ở service.
 */
public final class Uploads {

    private Uploads() {
    }

    public static List<AttachmentUpload> of(List<MultipartFile> files) throws IOException {
        List<AttachmentUpload> uploads = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !(file.isEmpty() && (file.getOriginalFilename() == null
                        || file.getOriginalFilename().isBlank()))) {
                    uploads.add(new AttachmentUpload(file.getOriginalFilename(), file.getContentType(),
                            file.getBytes()));
                }
            }
        }
        return uploads;
    }
}
