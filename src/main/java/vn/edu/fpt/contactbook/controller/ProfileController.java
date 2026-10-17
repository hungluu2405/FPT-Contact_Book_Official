package vn.edu.fpt.contactbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.dto.ProfileForm;
import vn.edu.fpt.contactbook.dto.ProfileView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AvatarStorage;
import vn.edu.fpt.contactbook.service.ProfileService;

import java.io.IOException;
import java.time.Duration;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private static final String VIEW = "common/profile";
    private static final String AVATAR_FIELD = "avatar";

    private final ProfileService profileService;
    private final AvatarStorage avatarStorage;

    /** Bỏ khoảng trắng hai đầu; chuỗi rỗng thành null (email không bắt buộc). */
    @InitBinder("form")
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/profile")
    public String view(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ProfileView profile = profileService.getProfile(principal.getUserId());
        ProfileForm form = new ProfileForm();
        form.setEmail(profile.email());
        form.setAddress(profile.address());
        model.addAttribute("form", form);
        return prepare(model, profile);
    }

    @PostMapping("/profile")
    public String update(@AuthenticationPrincipal AppUserPrincipal principal,
                         @Valid @ModelAttribute("form") ProfileForm form, BindingResult bindingResult,
                         @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                         Model model, HttpServletRequest request, RedirectAttributes redirectAttributes)
            throws IOException {
        if (!bindingResult.hasErrors()) {
            AvatarUpload upload = avatar == null || avatar.isEmpty()
                    ? null
                    : new AvatarUpload(avatar.getContentType(), avatar.getBytes());
            try {
                profileService.updateProfile(principal.getUserId(), form, upload, request.getRemoteAddr());
                redirectAttributes.addFlashAttribute("successMessage", "Đã lưu hồ sơ");
                return "redirect:/profile";
            } catch (BusinessException e) {
                if (AVATAR_FIELD.equals(e.getField())) {
                    // Ảnh không nằm trong ProfileForm (form không chứa kiểu web) nên báo lỗi riêng.
                    model.addAttribute("avatarError", e.getMessage());
                } else if (e.getField() == null) {
                    bindingResult.reject("profile.business", e.getMessage());
                } else {
                    bindingResult.rejectValue(e.getField(), "profile.business", e.getMessage());
                }
            }
        }
        return prepare(model, profileService.getProfile(principal.getUserId()));
    }

    /** Ảnh đại diện chỉ trả cho người đã đăng nhập (SecurityConfig: anyRequest().authenticated()). */
    @GetMapping(AvatarStorage.URL_PREFIX + "{fileName}")
    public ResponseEntity<Resource> avatar(@PathVariable String fileName) {
        return avatarStorage.resolve(fileName)
                .map(file -> ResponseEntity.ok()
                        .contentType(fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .<Resource>body(new FileSystemResource(file)))
                .orElse(ResponseEntity.notFound().build());
    }

    private String prepare(Model model, ProfileView profile) {
        model.addAttribute("profile", profile);
        model.addAttribute("activeMenu", "profile");
        return VIEW;
    }
}
