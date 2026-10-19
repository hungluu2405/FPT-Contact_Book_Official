package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.dto.AuditLogFilter;
import vn.edu.fpt.contactbook.dto.AuditLogView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.AuditLogService;
import vn.edu.fpt.contactbook.util.PageUtils;

/**
 * Nhật ký hệ thống (SRS FR-SYS-01), chỉ ADMIN (/admin/** trong SecurityConfig và kiểm tra lại ở service). CHỈ ĐỌC:
 * controller này chỉ có GET, không có sửa hay xóa dòng nhật ký.
 */
@Controller
@RequestMapping(AuditLogController.BASE)
@RequiredArgsConstructor
public class AuditLogController {

    static final String BASE = "/admin/audit-logs";
    private static final String MENU = "audit-logs";

    private final AuditLogService auditLogService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor,
                       @ModelAttribute("filter") AuditLogFilter filter, BindingResult bindingResult,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<AuditLogView> items = Page.empty();
        if (!bindingResult.hasErrors()) {
            try {
                items = auditLogService.search(actor, filter, page);
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
            }
        }
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE, PageUtils.params("keyword", filter.getKeyword(),
                "action", filter.getAction(), "entityType", filter.getEntityType(), "from", filter.getFrom(),
                "to", filter.getTo())));
        model.addAttribute("actions", AuditActions.actions());
        model.addAttribute("entityTypes", AuditActions.entityTypes());
        model.addAttribute("activeMenu", MENU);
        return "admin/audit-logs/list";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("item", auditLogService.detail(actor, id));
        model.addAttribute("activeMenu", MENU);
        return "admin/audit-logs/detail";
    }
}
