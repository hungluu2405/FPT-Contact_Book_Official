package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.AttachmentDownload;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.dto.NotificationDetail;
import vn.edu.fpt.contactbook.dto.NotificationForm;
import vn.edu.fpt.contactbook.dto.NotificationListItem;
import vn.edu.fpt.contactbook.dto.SentNotificationRow;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.NotificationService;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Thông báo (SRS FR-NOTI-01..04): hộp thư của mọi người dùng, chi tiết (người nhận hoặc người gửi), tải tệp đính kèm,
 * gửi và thống kê đã gửi (ADMIN, trưởng bộ môn, giảng viên). Phạm vi và quyền kiểm tra lại ở NotificationService.
 */
@Controller
@RequestMapping(NotificationController.BASE)
@RequiredArgsConstructor
public class NotificationController {

    static final String BASE = "/notifications";
    private static final String SENDERS = "hasAnyRole('ADMIN', 'HEAD_OF_DEPARTMENT', 'TEACHER')";

    private final NotificationService notificationService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String inbox(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Page<NotificationListItem> items = notificationService.inbox(actor, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE, PageUtils.params()));
        model.addAttribute("activeMenu", "notifications");
        return "notifications/inbox";
    }

    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal AppUserPrincipal actor, RedirectAttributes redirectAttributes) {
        int count = notificationService.markAllRead(actor);
        redirectAttributes.addFlashAttribute("successMessage", "Đã đánh dấu " + count + " thông báo là đã đọc");
        return "redirect:" + BASE;
    }

    /** read: "true" đã đọc, "false" chưa đọc, khác là tất cả (bộ lọc danh sách người nhận của người gửi). */
    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                         @RequestParam(required = false) String read, @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Boolean readFilter = "true".equals(read) ? Boolean.TRUE : "false".equals(read) ? Boolean.FALSE : null;
        NotificationDetail detail = notificationService.open(actor, id, readFilter, page);
        model.addAttribute("item", detail);
        model.addAttribute("readFilter", readFilter == null ? "all" : readFilter.toString());
        if (detail.recipients() != null) {
            model.addAttribute("pager", PageUtils.pager(detail.recipients(), BASE + "/" + id,
                    PageUtils.params("read", readFilter)));
        }
        model.addAttribute("activeMenu", detail.sender() ? "notifications-sent" : "notifications");
        return "notifications/detail";
    }

    @GetMapping("/{id:\\d+}/files/{fileId:\\d+}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                             @PathVariable Long fileId) {
        AttachmentDownload file = notificationService.download(actor, id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    @GetMapping("/new")
    @PreAuthorize(SENDERS)
    public String form(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("form", new NotificationForm());
        return formView(actor, model);
    }

    @PostMapping("/new")
    @PreAuthorize(SENDERS)
    public String send(@AuthenticationPrincipal AppUserPrincipal actor,
                       @Valid @ModelAttribute("form") NotificationForm form, BindingResult bindingResult,
                       @RequestParam(value = "files", required = false) List<MultipartFile> files, Model model,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = notificationService.send(actor, form, uploads(files), request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi thông báo");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return formView(actor, model);
    }

    @GetMapping("/sent")
    @PreAuthorize(SENDERS)
    public String sent(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<SentNotificationRow> items = notificationService.sent(actor, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE + "/sent", PageUtils.params()));
        model.addAttribute("activeMenu", "notifications-sent");
        return "notifications/sent";
    }

    private String formView(AppUserPrincipal actor, Model model) {
        model.addAttribute("options", notificationService.formOptions(actor));
        model.addAttribute("priorities", NotificationPriority.values());
        model.addAttribute("activeMenu", "notifications-new");
        return "notifications/form";
    }

    /** Bỏ phần tệp rỗng trình duyệt gửi khi không chọn tệp; tệp còn lại kiểm tra ở service. */
    private static List<AttachmentUpload> uploads(List<MultipartFile> files) throws IOException {
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
