package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetAttemptService;
import vn.edu.fpt.contactbook.security.PasswordResetCodes;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.PasswordResetMailer;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static vn.edu.fpt.contactbook.support.TestAccounts.PASSWORD;

/**
 * Quên mật khẩu qua email trên web và REST API, với database test thật; chỉ bộ gửi email là giả (bắt mã đã gửi).
 */
@IntegrationTest
class PasswordResetIntegrationTest {

    private static final String NEW_PASSWORD = "MatKhauMoi2026";
    private static final String UNKNOWN_PHONE = "0999000333";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordResetAttemptService resetAttempts;
    @Autowired LoginAttemptService loginAttempts;
    @MockitoBean PasswordResetMailer mailer;

    /** Bộ đếm nằm trong bean singleton dùng chung giữa các test (không rollback theo transaction). */
    @AfterEach
    void clearAttempts() {
        for (String phone : new String[]{TestAccounts.STUDENT, TestAccounts.PARENT, UNKNOWN_PHONE}) {
            resetAttempts.reset(phone);
            loginAttempts.reset(phone);
        }
    }

    @Test
    @DisplayName("Trang đăng nhập có link Quên mật khẩu; hai trang quên mật khẩu mở cho người chưa đăng nhập")
    void pagesArePublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/forgot-password\"")));
        mockMvc.perform(get("/forgot-password")).andExpect(status().isOk()).andExpect(view().name("auth/forgot-password"));
        mockMvc.perform(get("/reset-password")).andExpect(status().isOk()).andExpect(view().name("auth/reset-password"));
    }

    @Test
    @DisplayName("Web: nhận mã qua email, đặt mật khẩu mới, đăng nhập được bằng mật khẩu mới, mật khẩu cũ hết dùng")
    void webFlowResetsPassword() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf()).param("phone", TestAccounts.STUDENT))
                .andExpect(redirectedUrl("/reset-password"))
                .andExpect(flash().attribute("codeSent", true));
        String code = sentCode("student@fpt.edu.vn");

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("phone", TestAccounts.STUDENT).param("code", code)
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login?reset"));

        User student = userRepository.findByPhone(TestAccounts.STUDENT).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, student.getPasswordHash())).isTrue();
        assertThat(student.isMustChangePassword()).isFalse();
        assertThat(lastAudit(TestAccounts.STUDENT_ID).getAction()).isEqualTo(AuditActions.PASSWORD_RESET_BY_EMAIL);
        mockMvc.perform(formLogin("/login").userParameter("phone").user(TestAccounts.STUDENT).password(NEW_PASSWORD))
                .andExpect(redirectedUrl("/student"));
        mockMvc.perform(formLogin("/login").userParameter("phone").user(TestAccounts.STUDENT).password(PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Web: mã sai hoặc mật khẩu yếu hiện lỗi cạnh ô, giữ nguyên trang")
    void webShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("phone", TestAccounts.STUDENT).param("code", "000000")
                        .param("newPassword", "ngan").param("confirmPassword", "khac"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attributeHasFieldErrors("form", "newPassword", "confirmPassword"));

        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("phone", TestAccounts.STUDENT).param("code", "000000")
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "code"))
                .andExpect(content().string(containsString("Mã xác nhận không đúng hoặc đã hết hạn")));
    }

    @Test
    @DisplayName("Web: thiếu CSRF token bị chặn (người chưa đăng nhập về trang đăng nhập, không gửi email)")
    void webRequiresCsrf() throws Exception {
        mockMvc.perform(post("/forgot-password").param("phone", TestAccounts.STUDENT))
                .andExpect(redirectedUrl("/login?error=expired"));
        mockMvc.perform(post("/reset-password").param("phone", TestAccounts.STUDENT).param("code", "000000")
                        .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/login?error=expired"));
        verifyNoInteractions(mailer);
    }

    @Test
    @DisplayName("API: request 202, confirm 204; refresh token cũ hết hiệu lực vì mật khẩu đã đổi")
    void apiFlowResetsPassword() throws Exception {
        String oldRefresh = json(postJson("/api/v1/auth/login",
                Map.of("phone", TestAccounts.PARENT, "password", PASSWORD))).get("refreshToken").asString();

        postJson("/api/v1/auth/password-reset/request", Map.of("phone", "+84 900.000.005"))
                .andExpect(status().isAccepted());
        String code = sentCode("parent@fpt.edu.vn");
        assertThat(lastAudit(TestAccounts.PARENT_ID).getAction()).isEqualTo(AuditActions.PASSWORD_RESET_REQUESTED);

        postJson("/api/v1/auth/password-reset/confirm", Map.of("phone", TestAccounts.PARENT, "code", code,
                "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD))
                .andExpect(status().isNoContent());

        postJson("/api/v1/auth/login", Map.of("phone", TestAccounts.PARENT, "password", NEW_PASSWORD))
                .andExpect(status().isOk());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API: số không tồn tại cũng nhận 202 nhưng không có email nào được gửi")
    void apiDoesNotRevealUnknownPhones() throws Exception {
        postJson("/api/v1/auth/password-reset/request", Map.of("phone", UNKNOWN_PHONE))
                .andExpect(status().isAccepted());

        verifyNoInteractions(mailer);
    }

    @Test
    @DisplayName("API: lỗi trả JSON (BUSINESS_ERROR gắn ô code, gửi mã quá dày, thiếu dữ liệu)")
    void apiErrors() throws Exception {
        JsonNode wrongCode = json(postJson("/api/v1/auth/password-reset/confirm", Map.of(
                "phone", TestAccounts.STUDENT, "code", "000000",
                "newPassword", NEW_PASSWORD, "confirmPassword", NEW_PASSWORD)).andExpect(status().isBadRequest()));
        assertThat(wrongCode.get("code").asString()).isEqualTo("BUSINESS_ERROR");
        assertThat(wrongCode.get("fieldErrors").get(0).get("field").asString()).isEqualTo("code");

        postJson("/api/v1/auth/password-reset/request", Map.of("phone", UNKNOWN_PHONE))
                .andExpect(status().isAccepted());
        JsonNode limited = json(postJson("/api/v1/auth/password-reset/request", Map.of("phone", UNKNOWN_PHONE))
                .andExpect(status().isBadRequest()));
        assertThat(limited.get("fieldErrors").get(0).get("field").asString()).isEqualTo("phone");

        JsonNode missing = json(postJson("/api/v1/auth/password-reset/confirm", Map.of())
                .andExpect(status().isBadRequest()));
        assertThat(missing.get("code").asString()).isEqualTo("VALIDATION_FAILED");
    }

    private String sentCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(email), anyString(), code.capture(), eq(PasswordResetCodes.WINDOW));
        assertThat(code.getValue()).matches("\\d{6}");
        return code.getValue();
    }

    private AuditLog lastAudit(long userId) {
        return auditLogRepository.findAll().stream()
                .filter(entry -> entry.getUser() != null && entry.getUser().getId() == userId)
                .max((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElseThrow();
    }

    private ResultActions postJson(String url, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return jsonMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
