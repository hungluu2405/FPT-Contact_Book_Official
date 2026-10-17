package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mỗi màn hình: đúng vai trò 200, sai vai trò 403, chưa đăng nhập về /login (CLAUDE.md mục 8).
 * Principal nạp thật từ FptContactBook_test qua AppUserDetailsService.
 */
@IntegrationTest
class AccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserDetailsService userDetailsService;

    @ParameterizedTest(name = "{0} GET {1} -> {2}")
    @CsvSource({
            "0900000001, /admin, 200",
            "0900000001, /teacher, 403",
            "0900000001, /student, 403",
            "0900000001, /parent, 403",
            "0900000002, /admin, 403",
            "0900000002, /teacher, 200",
            "0900000003, /teacher, 200",
            "0900000003, /student, 403",
            "0900000004, /admin, 403",
            "0900000004, /teacher, 403",
            "0900000004, /student, 200",
            "0900000004, /parent, 403",
            "0900000005, /parent, 200",
            "0900000005, /student, 403",
            "0900000005, /admin, 403"
    })
    @DisplayName("Trang chủ theo vai trò")
    void roleHomes(String phone, String path, int expectedStatus) throws Exception {
        mockMvc.perform(get(path).with(as(phone))).andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/admin", "/teacher", "/student", "/parent", "/profile", "/change-password",
            "/avatars/00000000-0000-0000-0000-000000000000.png", "/admin/anything"})
    @DisplayName("Chưa đăng nhập thì chuyển tới trang đăng nhập")
    void anonymousIsRedirectedToLogin(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0900000001", "0900000002", "0900000004", "0900000005"})
    @DisplayName("Mọi vai trò vào được hồ sơ và đổi mật khẩu")
    void sharedPagesForEveryRole(String phone) throws Exception {
        mockMvc.perform(get("/profile").with(as(phone))).andExpect(status().isOk());
        mockMvc.perform(get("/change-password").with(as(phone))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"0900000001, /admin", "0900000002, /teacher", "0900000004, /student", "0900000005, /parent"})
    @DisplayName("/ chuyển tới trang chủ theo vai trò")
    void rootRedirectsByRole(String phone, String home) throws Exception {
        mockMvc.perform(get("/").with(as(phone))).andExpect(redirectedUrl(home));
    }

    @Test
    @DisplayName("0900000002 thấy cả TEACHER và HEAD_OF_DEPARTMENT (bộ môn SE), menu không lặp")
    void teacherHomeShowsBothRoles() throws Exception {
        String html = mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER_HEAD)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Nguyễn Văn Trưởng"),
                        containsString("Giảng viên"),
                        containsString("Trưởng bộ môn"),
                        containsString("Bộ môn Kỹ thuật phần mềm (SE)"),
                        containsString("Sắp có"),
                        not(containsString("href=\"/admin\"")))))
                .andReturn().getResponse().getContentAsString();

        assertThat(html.split("href=\"/teacher\"", -1)).as("một mục menu giảng viên").hasSize(2);
    }

    @Test
    @DisplayName("Thanh trên có họ tên và nút đăng xuất dạng form POST có CSRF")
    void topbarHasLogoutForm() throws Exception {
        mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(
                        containsString("Quản trị hệ thống"),
                        containsString("action=\"/logout\" method=\"post\""),
                        containsString("name=\"_csrf\""))));
    }

    @Test
    @DisplayName("Đường dẫn không tồn tại trả 404")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/khong-ton-tai").with(as(TestAccounts.ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /logout không đăng xuất (chỉ POST)")
    void logoutRequiresPost() throws Exception {
        mockMvc.perform(get("/logout").with(as(TestAccounts.ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tài nguyên tĩnh không cần đăng nhập")
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/password-toggle.js")).andExpect(status().isOk());
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
