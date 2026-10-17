package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.TestAccounts.PASSWORD;

@IntegrationTest
class ChangePasswordIntegrationTest {

    private static final String NEW_PASSWORD = "MatKhauMoi2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppUserDetailsService userDetailsService;

    @Test
    @DisplayName("must_change_password = 1: bị ép đổi, đổi xong vào được trang chủ")
    void forcedChangeFlow() throws Exception {
        User student = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow();
        student.setMustChangePassword(true);
        userRepository.saveAndFlush(student);

        MvcResult login = mockMvc.perform(formLogin("/login").userParameter("phone")
                        .user(TestAccounts.STUDENT).password(PASSWORD))
                .andExpect(redirectedUrl("/student"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/student").session(session)).andExpect(redirectedUrl("/change-password"));
        mockMvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/change-password"));
        mockMvc.perform(get("/").session(session)).andExpect(redirectedUrl("/change-password"));
        mockMvc.perform(get("/css/app.css").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/change-password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn cần đổi mật khẩu trước khi tiếp tục")));

        long auditBefore = auditLogRepository.count();
        mockMvc.perform(post("/change-password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Đổi mật khẩu thành công"));

        // Session id đổi sau khi đổi mật khẩu; MockMvc vẫn dùng cùng đối tượng session.
        mockMvc.perform(get("/student").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/profile").session(session)).andExpect(status().isOk());

        User updated = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow();
        assertThat(updated.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(auditLogRepository.count()).isEqualTo(auditBefore + 1);
        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditActions.CHANGE_PASSWORD);
            assertThat(entry.getEntityId()).isEqualTo(TestAccounts.STUDENT_ID);
        });

        mockMvc.perform(formLogin("/login").userParameter("phone").user(TestAccounts.STUDENT).password(NEW_PASSWORD))
                .andExpect(redirectedUrl("/student"));
    }

    @Test
    @DisplayName("Người dùng bình thường tự đổi mật khẩu")
    void voluntaryChange() throws Exception {
        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/"));

        assertThat(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findById(TestAccounts.ADMIN_ID).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Sai mật khẩu hiện tại: báo lỗi, không đổi")
    void wrongCurrentPassword() throws Exception {
        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN)).with(csrf())
                        .param("currentPassword", "wrong-current")
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại không đúng")));

        assertUnchanged();
    }

    @Test
    @DisplayName("Vi phạm quy tắc mật khẩu mới: báo lỗi cạnh ô, không đổi")
    void policyViolations() throws Exception {
        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "short1")
                        .param("confirmPassword", "other"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu mới phải có ít nhất 8 ký tự")))
                .andExpect(content().string(containsString("Mật khẩu nhập lại không khớp")));

        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(content().string(containsString("Mật khẩu mới phải khác mật khẩu hiện tại")));

        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN)).with(csrf())
                        .param("currentPassword", "")
                        .param("newPassword", "")
                        .param("confirmPassword", ""))
                .andExpect(content().string(containsString("Vui lòng nhập mật khẩu hiện tại")));

        assertUnchanged();
    }

    @Test
    @DisplayName("POST đổi mật khẩu thiếu CSRF bị 403")
    void changeWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/change-password").with(asUser(TestAccounts.ADMIN))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isForbidden());

        assertUnchanged();
    }

    private void assertUnchanged() {
        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(TestAccounts.ADMIN_ID).orElseThrow().getPasswordHash())).isTrue();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
