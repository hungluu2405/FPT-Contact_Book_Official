package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.Page;
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
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.dto.ClubFilter;
import vn.edu.fpt.contactbook.dto.ClubForm;
import vn.edu.fpt.contactbook.dto.ClubView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ClubService;
import vn.edu.fpt.contactbook.util.PageUtils;

import java.io.IOException;

/**
 * Quản lý câu lạc bộ (SRS FR-CLUB-01, chỉ ADMIN): danh sách, thêm, sửa (kèm logo), ngừng hoạt động / mở lại, chỉ
 * định hoặc chuyển giao chủ nhiệm. Không có chức năng xóa.
 */
@Controller
@RequestMapping(AdminClubController.BASE)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminClubController {

    static final String BASE = "/admin/clubs";
    private static final String MENU = "admin-clubs";

    private final ClubService clubService;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("filter") ClubFilter filter,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<ClubView> items = clubService.list(actor, filter, page);
        model.addAttribute("items", items);
        model.addAttribute("pager", PageUtils.pager(items, BASE,
                PageUtils.params("keyword", filter.getKeyword(), "status", filter.getStatus())));
        model.addAttribute("statuses", ClubStatus.values());
        model.addAttribute("activeMenu", MENU);
        return "admin/clubs/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new ClubForm());
        return formView(model, null);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @Valid @ModelAttribute("form") ClubForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "logo", required = false) MultipartFile logo, Model model,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        if (!bindingResult.hasErrors()) {
            try {
                Long id = clubService.create(actor, form, upload(logo), request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã thêm câu lạc bộ");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                applyErrors(bindingResult, e, model);
            }
        }
        return formView(model, null);
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("item", clubService.detail(actor, id));
        model.addAttribute("activeMenu", MENU);
        return "admin/clubs/detail";
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("form", clubService.editForm(actor, id));
        return formView(model, id);
    }

    @PostMapping("/{id:\\d+}/edit")
    public String edit(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                       @Valid @ModelAttribute("form") ClubForm form, BindingResult bindingResult,
                       @RequestParam(value = "logo", required = false) MultipartFile logo, Model model,
                       HttpServletRequest request, RedirectAttributes redirectAttributes) throws IOException {
        if (!bindingResult.hasErrors()) {
            try {
                clubService.update(actor, id, form, upload(logo), request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu câu lạc bộ");
                return "redirect:" + BASE + "/" + id;
            } catch (BusinessException e) {
                applyErrors(bindingResult, e, model);
            }
        }
        return formView(model, id);
    }

    @PostMapping("/{id:\\d+}/deactivate")
    public String deactivate(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes, "Đã ngừng hoạt động câu lạc bộ",
                () -> clubService.deactivate(actor, id, request.getRemoteAddr()));
    }

    @PostMapping("/{id:\\d+}/activate")
    public String activate(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                           HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes, "Đã mở lại câu lạc bộ",
                () -> clubService.activate(actor, id, request.getRemoteAddr()));
    }

    @PostMapping("/{id:\\d+}/president")
    public String assignPresident(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable Long id,
                                  @RequestParam(required = false) String studentCode, HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        return FormErrors.runThenRedirect(BASE + "/" + id, redirectAttributes, "Đã chỉ định chủ nhiệm",
                () -> clubService.assignPresident(actor, id, studentCode, request.getRemoteAddr()));
    }

    /** Lỗi logo (ô tệp không thuộc form object) hiện riêng; lỗi khác gắn vào ô tương ứng. */
    private static void applyErrors(BindingResult bindingResult, BusinessException e, Model model) {
        String logoError = e.getFieldErrors().get("logo");
        if (logoError != null) {
            model.addAttribute("logoError", logoError);
            return;
        }
        FormErrors.apply(bindingResult, e);
    }

    static AvatarUpload upload(MultipartFile file) throws IOException {
        return file == null || file.isEmpty() ? null : new AvatarUpload(file.getContentType(), file.getBytes());
    }

    /** id = null khi thêm mới. */
    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("advisors", clubService.advisors());
        model.addAttribute("activeMenu", MENU);
        return "admin/clubs/form";
    }
}
