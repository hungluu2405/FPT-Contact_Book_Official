package vn.edu.fpt.contactbook.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("errorMessageKey", switch (error) {
                case "locked" -> "auth.login.temporarily-locked";
                case "disabled" -> "auth.login.disabled";
                case "norole" -> "auth.login.no-role";
                case "expired" -> "auth.login.session-expired";
                default -> "auth.login.error";
            });
        }
        if (logout != null) {
            model.addAttribute("infoMessageKey", "auth.logout.success");
        }
        return "auth/login";
    }
}
