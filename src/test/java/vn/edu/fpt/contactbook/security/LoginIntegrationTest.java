package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.TestAccounts.PASSWORD;

@IntegrationTest
class LoginIntegrationTest {

    private static final String UNKNOWN_PHONE = "0999000111";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoginAttemptService loginAttemptService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Bộ đếm nằm trong bean singleton dùng chung giữa các test: xóa bằng chính API reset. */
    @AfterEach
    void clearLoginAttempts() {
        for (String phone : new String[]{TestAccounts.ADMIN, TestAccounts.TEACHER, UNKNOWN_PHONE}) {
            loginAttemptService.reset(phone);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0900000001, /admin, ADMIN",
            "0900000002, /teacher, TEACHER",
            "0900000003, /teacher, TEACHER",
            "0900000004, /student, STUDENT",
            "0900000005, /parent, PARENT"
    })
    @DisplayName("Đăng nhập đúng chuyển tới trang chủ theo vai trò")
    void redirectsToRoleHome(String phone, String home, String role) throws Exception {
        login(phone, PASSWORD)
                .andExpect(redirectedUrl(home))
                .andExpect(authenticated().withUsername(phone).withRoles(roleSetOf(phone, role)));
    }

    @ParameterizedTest
    @CsvSource({"+84900000001", "84900000001", "'0900 000 001'", "0900.000.001", "0900-000-001"})
    @DisplayName("Số dạng +84, 84, có khoảng trắng/chấm/gạch đăng nhập được")
    void acceptsNormalizedPhoneFormats(String phone) throws Exception {
        login(phone, PASSWORD)
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated().withUsername(TestAccounts.ADMIN));
    }

    @Test
    @DisplayName("Sai mật khẩu, số không tồn tại, số sai định dạng: cùng một thông báo chung")
    void wrongCredentialsUseGenericError() throws Exception {
        login(TestAccounts.ADMIN, "wrong-password").andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        login(UNKNOWN_PHONE, PASSWORD).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        login("12345", PASSWORD).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        login("", "").andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());

        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Số điện thoại hoặc mật khẩu không đúng")));
    }

    @Test
    @DisplayName("Mật khẩu quá 72 byte bị từ chối bình thường, không gây lỗi 500")
    void veryLongPasswordIsRejectedCleanly() throws Exception {
        login(TestAccounts.ADMIN, "a".repeat(100)).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Sai 5 lần thì khóa tạm, kể cả khi sau đó nhập đúng mật khẩu")
    void locksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(TestAccounts.ADMIN, "wrong-" + i).andExpect(redirectedUrl("/login?error"));
        }

        login(TestAccounts.ADMIN, PASSWORD)
                .andExpect(redirectedUrl("/login?error=locked"))
                .andExpect(unauthenticated());
        login("+84900000001", PASSWORD)
                .andExpect(redirectedUrl("/login?error=locked"));

        mockMvc.perform(get("/login").param("error", "locked"))
                .andExpect(content().string(containsString(
                        "Tài khoản tạm khóa do nhập sai nhiều lần, vui lòng thử lại sau")));
    }

    @Test
    @DisplayName("Số không tồn tại cũng bị đếm và khóa tạm")
    void locksUnknownPhoneToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(UNKNOWN_PHONE, "wrong").andExpect(redirectedUrl("/login?error"));
        }

        login(UNKNOWN_PHONE, "wrong").andExpect(redirectedUrl("/login?error=locked"));
    }

    @Test
    @DisplayName("Đăng nhập đúng xóa bộ đếm sai")
    void successResetsCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(TestAccounts.ADMIN, "wrong").andExpect(redirectedUrl("/login?error"));
        }
        login(TestAccounts.ADMIN, PASSWORD).andExpect(redirectedUrl("/admin"));
        for (int i = 0; i < 4; i++) {
            login(TestAccounts.ADMIN, "wrong").andExpect(redirectedUrl("/login?error"));
        }

        login(TestAccounts.ADMIN, PASSWORD).andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("is_active = 0: mật khẩu đúng báo bị khóa, mật khẩu sai báo lỗi chung")
    void inactiveAccount() throws Exception {
        User teacher = userRepository.findById(TestAccounts.TEACHER_ID).orElseThrow();
        teacher.setActive(false);
        userRepository.saveAndFlush(teacher);

        login(TestAccounts.TEACHER, "wrong").andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        login(TestAccounts.TEACHER, PASSWORD)
                .andExpect(redirectedUrl("/login?error=disabled"))
                .andExpect(unauthenticated());

        mockMvc.perform(get("/login").param("error", "disabled"))
                .andExpect(content().string(containsString(
                        "Tài khoản đã bị khóa, vui lòng liên hệ quản trị viên")));
    }

    @Test
    @DisplayName("Người dùng không có vai trò nào bị đăng xuất và báo lỗi")
    void userWithoutRoleIsLoggedOut() throws Exception {
        User user = new User();
        user.setPhone("0911222333");
        user.setFullName("Không vai trò");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);

        login("0911222333", PASSWORD)
                .andExpect(redirectedUrl("/login?error=norole"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Đăng nhập thành công cập nhật last_login_at và ghi audit LOGIN kèm IP")
    void recordsLastLoginAndAudit() throws Exception {
        long before = auditLogRepository.count();
        LocalDateTime start = AppTime.now().minusSeconds(5);

        login(TestAccounts.ADMIN, PASSWORD).andExpect(redirectedUrl("/admin"));

        User admin = userRepository.findById(TestAccounts.ADMIN_ID).orElseThrow();
        assertThat(admin.getLastLoginAt()).isAfter(start);
        assertThat(Duration.between(start, admin.getLastLoginAt())).isLessThan(Duration.ofMinutes(1));
        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditActions.LOGIN);
            assertThat(entry.getEntityType()).isEqualTo("User");
            assertThat(entry.getEntityId()).isEqualTo(TestAccounts.ADMIN_ID);
            assertThat(entry.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(entry.getCreatedAt()).isAfter(start);
        });
    }

    @Test
    @DisplayName("Đăng nhập sai không ghi audit LOGIN")
    void failedLoginWritesNoAudit() throws Exception {
        long before = auditLogRepository.count();

        login(TestAccounts.ADMIN, "wrong").andExpect(redirectedUrl("/login?error"));

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("POST /login thiếu CSRF: không đăng nhập, quay lại trang đăng nhập báo hết phiên")
    void loginWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/login").param("phone", TestAccounts.ADMIN).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login?error=expired"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("Form đăng nhập để quá hạn phiên (mã CSRF cũ): báo hết phiên thay vì 403")
    void staleLoginFormRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/login").param("phone", TestAccounts.ADMIN).param("password", PASSWORD)
                        .with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?error=expired"))
                .andExpect(unauthenticated());

        mockMvc.perform(get("/login").param("error", "expired"))
                .andExpect(content().string(containsString("Phiên làm việc đã hết hạn, vui lòng đăng nhập lại")));
    }

    @Test
    @DisplayName("Bấm Đăng xuất sau khi phiên hết hạn: về trang đăng nhập thay vì 403")
    void staleLogoutRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/logout").with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?error=expired"));
    }

    @Test
    @DisplayName("Gửi form khác sau khi phiên hết hạn: về trang đăng nhập thay vì 403")
    void staleFormAfterSessionExpiryRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/change-password").with(csrf().useInvalidToken())
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/login?error=expired"));
    }

    @Test
    @DisplayName("Trang đăng nhập có CSRF, không có Đăng ký")
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"_csrf\""),
                        containsString("type=\"tel\""),
                        containsString("Quên mật khẩu? Vui lòng liên hệ quản trị viên."),
                        not(containsString("Đăng ký")))));
    }

    @Test
    @DisplayName("Đăng xuất bằng POST có CSRF")
    void logoutWithPost() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }

    private ResultActions login(String phone, String password) throws Exception {
        return mockMvc.perform(formLogin("/login").userParameter("phone").passwordParam("password")
                .user(phone).password(password));
    }

    private static String[] roleSetOf(String phone, String role) {
        return TestAccounts.TEACHER_HEAD.equals(phone)
                ? new String[]{"HEAD_OF_DEPARTMENT", "TEACHER"}
                : new String[]{role};
    }
}
