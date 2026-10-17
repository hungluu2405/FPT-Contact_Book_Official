package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.security.RoleHomeResolver;
import vn.edu.fpt.contactbook.service.AccountService;

/**
 * "/" chuyển tới trang chủ theo vai trò; 4 trang chủ dùng chung một template.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AccountService accountService;

    @GetMapping("/")
    public String root(@AuthenticationPrincipal AppUserPrincipal principal) {
        return RoleHomeResolver.homeFor(principal.getRoles())
                .map(home -> "redirect:" + home)
                .orElseThrow(() -> new AccessDeniedException("No role"));
    }

    @GetMapping("/admin")
    public String admin(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        return home(principal, model, "admin");
    }

    @GetMapping("/teacher")
    public String teacher(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        return home(principal, model, "teacher");
    }

    @GetMapping("/student")
    public String student(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        return home(principal, model, "student");
    }

    @GetMapping("/parent")
    public String parent(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        return home(principal, model, "parent");
    }

    private String home(AppUserPrincipal principal, Model model, String area) {
        model.addAttribute("area", area);
        model.addAttribute("activeMenu", area);
        model.addAttribute("fullName", principal.getFullName());
        model.addAttribute("roles", accountService.roleAssignments(principal.getUserId()));
        return "common/home";
    }
}
