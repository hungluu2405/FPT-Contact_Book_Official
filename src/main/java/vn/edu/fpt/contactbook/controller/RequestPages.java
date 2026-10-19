package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.LeaveRequestForm;
import vn.edu.fpt.contactbook.dto.OtherRequestForm;
import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.dto.RequestListItem;
import vn.edu.fpt.contactbook.dto.ReviewResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.service.RequestService.View;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Dựng model và xử lý form dùng chung cho các trang đơn từ (requests/*.html) của sinh viên, phụ huynh, giảng viên và
 * ADMIN. baseUrl là danh sách đơn của đường truy cập đó; chi tiết là baseUrl/{id}.
 */
@Component
@RequiredArgsConstructor
class RequestPages {

    /** Loại đơn sinh viên chọn được ở form đơn khác. */
    static final List<RequestType> OTHER_TYPES = Arrays.stream(RequestType.values())
            .filter(t -> t != RequestType.LEAVE).toList();

    private final RequestService requestService;

    /** Form đơn nghỉ mới: mặc định nghỉ hôm nay; tiêu đề để trống để service (hoặc script trên trang) tự điền. */
    static LeaveRequestForm newLeaveForm() {
        LeaveRequestForm form = new LeaveRequestForm();
        form.setFromDate(AppTime.today());
        form.setToDate(AppTime.today());
        return form;
    }

    String list(Model model, Page<RequestListItem> items, String baseUrl, String title, boolean canSendOther) {
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, baseUrl, PageUtils.params()));
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("pageTitle", title);
        model.addAttribute("canSendOther", canSendOther);
        return "requests/list";
    }

    /** Danh sách cần duyệt; status là chuỗi trên URL (mặc định PENDING, "ALL" là tất cả). */
    String reviewList(Model model, AppUserPrincipal actor, View view, String status, int page, String baseUrl,
                      String title) {
        RequestStatus filter = parseStatus(status);
        Page<RequestListItem> items = requestService.reviewQueue(actor, view, filter, page);
        String selected = filter == null ? "ALL" : filter.name();
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, baseUrl, PageUtils.params("status", selected)));
        model.addAttribute("status", selected);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("pageTitle", title);
        return "requests/review-list";
    }

    String leaveForm(Model model, AppUserPrincipal actor, View view, Long studentId, String baseUrl) {
        model.addAttribute("options", requestService.leaveOptions(actor, view, studentId));
        model.addAttribute("baseUrl", baseUrl);
        return "requests/leave-form";
    }

    String otherForm(Model model, String baseUrl) {
        model.addAttribute("types", OTHER_TYPES);
        model.addAttribute("baseUrl", baseUrl);
        return "requests/other-form";
    }

    /** Gửi đơn nghỉ: thành công thì chuyển tới chi tiết đơn, lỗi thì hiện lại form với lỗi cạnh từng ô. */
    String submitLeave(Model model, AppUserPrincipal actor, View view, Long studentId, LeaveRequestForm form,
                       BindingResult bindingResult, List<MultipartFile> files, HttpServletRequest request,
                       RedirectAttributes redirectAttributes, String baseUrl) throws IOException {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = requestService.createLeave(actor, view, studentId, form,
                        NotificationController.uploads(files), request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi đơn xin nghỉ");
                return "redirect:" + baseUrl + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return leaveForm(model, actor, view, studentId, baseUrl);
    }

    String submitOther(Model model, AppUserPrincipal actor, OtherRequestForm form, BindingResult bindingResult,
                       List<MultipartFile> files, HttpServletRequest request, RedirectAttributes redirectAttributes,
                       String baseUrl) throws IOException {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = requestService.createOther(actor, form, NotificationController.uploads(files),
                        request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi đơn");
                return "redirect:" + baseUrl + "/" + id;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        return otherForm(model, baseUrl);
    }

    String detail(Model model, RequestDetail detail, String baseUrl, String backLabel) {
        model.addAttribute("item", detail);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("backLabel", backLabel);
        return "requests/detail";
    }

    String cancel(AppUserPrincipal actor, Long id, HttpServletRequest request, RedirectAttributes redirectAttributes,
                  String baseUrl) {
        return FormErrors.runThenRedirect(baseUrl + "/" + id, redirectAttributes, "Đã hủy đơn",
                () -> requestService.cancel(actor, id, request.getRemoteAddr()));
    }

    /** Duyệt / từ chối; lỗi nghiệp vụ báo qua flash và giữ lại ghi chú đã nhập. */
    String review(AppUserPrincipal actor, View view, Long id, ReviewDecision decision, String comment,
                  HttpServletRequest request, RedirectAttributes redirectAttributes, String baseUrl) {
        try {
            ReviewResult result = requestService.review(actor, view, id, decision, comment, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", result.decision() == ReviewDecision.APPROVED
                    ? "Đã duyệt đơn; " + result.excusedSessions()
                    + " buổi vắng không phép được chuyển thành vắng có phép"
                    : "Đã từ chối đơn");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("reviewComment", comment);
        }
        return "redirect:" + baseUrl + "/" + id;
    }

    private static RequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return RequestStatus.PENDING;
        }
        return Arrays.stream(RequestStatus.values()).filter(s -> s.name().equals(status)).findFirst().orElse(null);
    }
}
