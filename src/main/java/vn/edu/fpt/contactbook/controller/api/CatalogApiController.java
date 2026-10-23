package vn.edu.fpt.contactbook.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.CatalogLookupService;
import vn.edu.fpt.contactbook.service.RequestService;

import java.util.List;

/** Dữ liệu dùng chung: danh sách học kỳ cho ô chọn học kỳ, tệp đính kèm của đơn. */
@Tag(name = "Common", description = "Học kỳ, tệp đính kèm của đơn")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogApiController {

    private final CatalogLookupService catalogLookupService;
    private final RequestService requestService;

    @Operation(summary = "Danh sách học kỳ (id, code, name)")
    @GetMapping("/semesters")
    public List<OptionView> semesters() {
        return catalogLookupService.semesters();
    }

    @Operation(summary = "Tải tệp đính kèm của đơn (người gửi, học sinh, phụ huynh liên kết, người duyệt)")
    @GetMapping("/requests/{id}/files/{fileId}")
    public ResponseEntity<Resource> requestFile(@AuthenticationPrincipal AppUserPrincipal actor,
                                                @PathVariable Long id, @PathVariable Long fileId) {
        return ApiDownloads.attachment(requestService.download(actor, id, fileId));
    }
}
