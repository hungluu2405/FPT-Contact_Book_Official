package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.edu.fpt.contactbook.dto.AttachmentDownload;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;

import java.nio.charset.StandardCharsets;

/**
 * Tải tệp minh chứng của đơn; mọi người đã đăng nhập gọi được, quyền (người gửi, sinh viên, phụ huynh liên kết,
 * người duyệt, ADMIN) kiểm tra ở RequestService.
 */
@Controller
@RequiredArgsConstructor
public class RequestFileController {

    private final RequestService requestService;

    @GetMapping("/requests/{id:\\d+}/files/{fileId:\\d+}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                             @PathVariable Long fileId) {
        AttachmentDownload file = requestService.download(actor, id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
}
