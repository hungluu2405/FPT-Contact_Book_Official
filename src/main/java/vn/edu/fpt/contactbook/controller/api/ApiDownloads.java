package vn.edu.fpt.contactbook.controller.api;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import vn.edu.fpt.contactbook.dto.AttachmentDownload;

import java.nio.charset.StandardCharsets;

/** Trả tệp đính kèm như web: đúng kiểu nội dung, tên tệp tiếng Việt (RFC 5987) trong Content-Disposition. */
final class ApiDownloads {

    private ApiDownloads() {
    }

    static ResponseEntity<Resource> attachment(AttachmentDownload file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
}
