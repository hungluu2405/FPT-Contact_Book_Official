package vn.edu.fpt.contactbook.controller.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.JwtTokenService;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.TestAccounts.PASSWORD;

/**
 * REST API nền tảng (giai đoạn 9b): đăng nhập, làm mới token, /me, đổi mật khẩu, lỗi JSON, tách khỏi chuỗi web.
 */
@IntegrationTest
class AuthApiIntegrationTest {

    static final String UNKNOWN_PHONE = "0999000222";
    static final String BAD_CREDENTIALS_MESSAGE = "Số điện thoại hoặc mật khẩu không đúng";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired LoginAttemptService loginAttemptService;
    @Autowired PasswordEncoder passwordEncoder;
    @Value("${app.jwt.secret}") String jwtSecret;

    /** Bộ đếm sai nằm trong bean singleton dùng chung giữa các test. */
    @AfterEach
    void clearLoginAttempts() {
        for (String phone : new String[]{TestAccounts.ADMIN, TestAccounts.STUDENT, UNKNOWN_PHONE}) {
            loginAttemptService.reset(phone);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0900000001, 1, ADMIN",
            "0900000002, 2, HEAD_OF_DEPARTMENT|TEACHER",
            "0900000003, 3, TEACHER",
            "0900000004, 4, STUDENT",
            "0900000005, 5, PARENT"
    })
    @DisplayName("5 tài khoản mẫu đăng nhập được, nhận cặp token và thông tin người dùng")
    void loginReturnsTokens(String phone, long userId, String roles) throws Exception {
        JsonNode body = json(login(phone, PASSWORD).andExpect(status().isOk()));

        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(body.get("accessToken").asString()).isNotBlank();
        assertThat(body.get("refreshToken").asString()).isNotBlank().isNotEqualTo(body.get("accessToken").asString());
        assertThat(body.get("user").get("id").asLong()).isEqualTo(userId);
        assertThat(body.get("user").get("phone").asString()).isEqualTo(phone);
        assertThat(texts(body.get("user").get("roles"))).containsExactly(roles.split("\\|"));
        assertThat(body.get("user").get("mustChangePassword").asBoolean()).isFalse();
        assertThat(body.toString()).doesNotContain("password_hash").doesNotContain("$2a$");
    }

    @Test
    @DisplayName("Số dạng +84 có khoảng trắng đăng nhập được; không cần CSRF, không tạo session")
    void loginIsStateless() throws Exception {
        MvcResult result = login("+84 900 000 004", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(json(result).get("user").get("id").asLong()).isEqualTo(TestAccounts.STUDENT_ID);
    }

    @Test
    @DisplayName("Sai mật khẩu, số không tồn tại, số sai định dạng: cùng 401 BAD_CREDENTIALS, cùng thông báo")
    void wrongCredentialsUseGenericError() throws Exception {
        for (String[] credentials : new String[][]{{TestAccounts.ADMIN, "wrong"}, {UNKNOWN_PHONE, PASSWORD},
                {"12345", PASSWORD}}) {
            JsonNode error = json(login(credentials[0], credentials[1]).andExpect(status().isUnauthorized()));
            assertError(error, 401, "BAD_CREDENTIALS", BAD_CREDENTIALS_MESSAGE);
        }
    }

    @Test
    @DisplayName("Thiếu trường: 400 VALIDATION_FAILED kèm lỗi từng ô; body không phải JSON: 400")
    void validationErrors() throws Exception {
        JsonNode error = json(postJson("/api/v1/auth/login", "{}").andExpect(status().isBadRequest()));

        assertThat(error.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldErrors(error)).containsEntry("phone", "Vui lòng nhập số điện thoại")
                .containsEntry("password", "Vui lòng nhập mật khẩu");

        JsonNode unreadable = json(postJson("/api/v1/auth/login", "not json").andExpect(status().isBadRequest()));
        assertThat(unreadable.get("code").asString()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Sai 5 lần thì khóa tạm (429), kể cả mật khẩu đúng; dùng chung bộ đếm với web")
    void locksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(TestAccounts.ADMIN, "wrong-" + i).andExpect(status().isUnauthorized());
        }

        JsonNode error = json(login(TestAccounts.ADMIN, PASSWORD).andExpect(status().isTooManyRequests()));
        assertError(error, 429, "ACCOUNT_LOCKED_TEMPORARILY",
                "Tài khoản tạm khóa do nhập sai nhiều lần, vui lòng thử lại sau");
        assertThat(loginAttemptService.isLocked(TestAccounts.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("is_active = 0: mật khẩu đúng trả 403 ACCOUNT_DISABLED, mật khẩu sai vẫn lỗi chung")
    void disabledAccount() throws Exception {
        deactivate(TestAccounts.STUDENT_ID);

        login(TestAccounts.STUDENT, "wrong").andExpect(status().isUnauthorized());
        JsonNode error = json(login(TestAccounts.STUDENT, PASSWORD).andExpect(status().isForbidden()));
        assertError(error, 403, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa, vui lòng liên hệ quản trị viên");
    }

    @Test
    @DisplayName("Tài khoản chưa có vai trò: 403 NO_ROLE, không phát token")
    void userWithoutRole() throws Exception {
        User user = new User();
        user.setPhone("0911222444");
        user.setFullName("Không vai trò");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);

        JsonNode error = json(login("0911222444", PASSWORD).andExpect(status().isForbidden()));
        assertThat(error.get("code").asString()).isEqualTo("NO_ROLE");
        assertThat(error.has("accessToken")).isFalse();
    }

    @Test
    @DisplayName("Audit: đăng nhập đúng ghi LOGIN kèm IP; đăng nhập sai ghi LOGIN_FAILED, không ghi mật khẩu")
    void loginIsAudited() throws Exception {
        long lastId = lastAuditId();

        login(TestAccounts.STUDENT, PASSWORD).andExpect(status().isOk());
        login(TestAccounts.STUDENT, "wrong-secret").andExpect(status().isUnauthorized());

        List<AuditLog> audits = auditsAfter(lastId);
        assertThat(audits).extracting(AuditLog::getAction)
                .containsExactly(AuditActions.LOGIN, AuditActions.LOGIN_FAILED);
        assertThat(audits).allSatisfy(entry -> {
            assertThat(entry.getEntityId()).isEqualTo(TestAccounts.STUDENT_ID);
            assertThat(entry.getIpAddress()).isEqualTo("127.0.0.1");
        });
        assertThat(audits.getLast().getDetail()).isEqualTo("reason=BAD_CREDENTIALS").doesNotContain("wrong-secret");
        assertThat(userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("/me với access token hợp lệ: 200 kèm thông tin, không tạo session")
    void meWithValidToken() throws Exception {
        String access = tokens(TestAccounts.TEACHER_HEAD).get("accessToken").asString();

        MvcResult result = mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        JsonNode me = json(result);
        assertThat(me.get("id").asLong()).isEqualTo(TestAccounts.TEACHER_HEAD_ID);
        assertThat(me.get("fullName").asString()).isNotBlank();
        assertThat(texts(me.get("roles"))).containsExactly("HEAD_OF_DEPARTMENT", "TEACHER");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("/me thiếu token, token rác, token hết hạn, token ký khóa khác, dùng refresh token: 401 JSON")
    void meRejectsInvalidTokens() throws Exception {
        String refresh = tokens(TestAccounts.STUDENT).get("refreshToken").asString();
        JwtTokenService past = tokenService(Instant.now().minus(Duration.ofHours(1)), jwtSecret);
        JwtTokenService foreign = tokenService(Instant.now(), "another-signing-key-0123456789abcdef");
        String hash = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getPasswordHash();

        assertUnauthorized(mockMvc.perform(get("/api/v1/me")));
        for (String token : List.of("garbage", refresh, past.issue(TestAccounts.STUDENT_ID, hash).accessToken(),
                foreign.issue(TestAccounts.STUDENT_ID, hash).accessToken())) {
            assertUnauthorized(mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token))));
        }
    }

    @Test
    @DisplayName("Tài khoản bị khóa sau khi đã có token: request kế tiếp 401")
    void deactivatedAfterLogin() throws Exception {
        String access = tokens(TestAccounts.STUDENT).get("accessToken").asString();
        deactivate(TestAccounts.STUDENT_ID);

        assertUnauthorized(mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(access))));
    }

    @Test
    @DisplayName("Refresh: đổi được cặp token mới, access token mới dùng được")
    void refreshIssuesNewTokens() throws Exception {
        String refresh = tokens(TestAccounts.PARENT).get("refreshToken").asString();

        JsonNode renewed = json(postJson("/api/v1/auth/refresh", body(Map.of("refreshToken", refresh)))
                .andExpect(status().isOk()));

        assertThat(renewed.get("user").get("id").asLong()).isEqualTo(TestAccounts.PARENT_ID);
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renewed.get("accessToken").asString())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Refresh bằng access token, chuỗi rác hoặc thiếu token: lỗi, không phát token")
    void refreshRejectsInvalidTokens() throws Exception {
        String access = tokens(TestAccounts.PARENT).get("accessToken").asString();

        for (String token : List.of(access, "garbage")) {
            JsonNode error = json(postJson("/api/v1/auth/refresh", body(Map.of("refreshToken", token)))
                    .andExpect(status().isUnauthorized()));
            assertThat(error.get("code").asString()).isEqualTo("UNAUTHORIZED");
        }
        postJson("/api/v1/auth/refresh", "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Refresh khi tài khoản đã bị khóa: 403 ACCOUNT_DISABLED")
    void refreshForDisabledAccount() throws Exception {
        String refresh = tokens(TestAccounts.STUDENT).get("refreshToken").asString();
        deactivate(TestAccounts.STUDENT_ID);

        JsonNode error = json(postJson("/api/v1/auth/refresh", body(Map.of("refreshToken", refresh)))
                .andExpect(status().isForbidden()));
        assertThat(error.get("code").asString()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("Đổi mật khẩu: trả token mới, ghi audit; refresh token cũ hết hiệu lực; đăng nhập bằng mật khẩu mới")
    void changePasswordRotatesTokens() throws Exception {
        JsonNode old = tokens(TestAccounts.STUDENT);
        long lastId = lastAuditId();

        JsonNode renewed = json(changePassword(old.get("accessToken").asString(), PASSWORD, "NewPass123", "NewPass123")
                .andExpect(status().isOk()));

        assertThat(renewed.get("user").get("mustChangePassword").asBoolean()).isFalse();
        assertThat(auditsAfter(lastId)).extracting(AuditLog::getAction).containsExactly(AuditActions.CHANGE_PASSWORD);
        postJson("/api/v1/auth/refresh", body(Map.of("refreshToken", old.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", body(Map.of("refreshToken", renewed.get("refreshToken").asString())))
                .andExpect(status().isOk());
        login(TestAccounts.STUDENT, "NewPass123").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đổi mật khẩu sai mật khẩu hiện tại: 400 BUSINESS_ERROR gắn ô currentPassword; thiếu ô: 400")
    void changePasswordErrors() throws Exception {
        String access = tokens(TestAccounts.STUDENT).get("accessToken").asString();

        JsonNode error = json(changePassword(access, "wrong", "NewPass123", "NewPass123")
                .andExpect(status().isBadRequest()));
        assertError(error, 400, "BUSINESS_ERROR", "Mật khẩu hiện tại không đúng");
        assertThat(fieldErrors(error)).containsOnlyKeys("currentPassword");

        JsonNode missing = json(mockMvc.perform(post("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()));
        assertThat(fieldErrors(missing)).containsOnlyKeys("currentPassword", "newPassword", "confirmPassword");
    }

    @Test
    @DisplayName("must_change_password = 1: /me và đổi mật khẩu được, API khác 403 PASSWORD_CHANGE_REQUIRED")
    void mustChangePasswordBlocksOtherApis() throws Exception {
        User student = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow();
        student.setMustChangePassword(true);
        userRepository.saveAndFlush(student);
        JsonNode tokens = tokens(TestAccounts.STUDENT);
        String access = tokens.get("accessToken").asString();
        assertThat(tokens.get("user").get("mustChangePassword").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk());
        JsonNode error = json(mockMvc.perform(get("/api/v1/schedule").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isForbidden()));
        assertThat(error.get("code").asString()).isEqualTo("PASSWORD_CHANGE_REQUIRED");

        JsonNode renewed = json(changePassword(access, PASSWORD, "NewPass123", "NewPass123")
                .andExpect(status().isOk()));
        assertThat(renewed.get("user").get("mustChangePassword").asBoolean()).isFalse();
        mockMvc.perform(get("/api/v1/schedule").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Đăng xuất: 204 khi có token, 401 khi không")
    void logout() throws Exception {
        String access = tokens(TestAccounts.STUDENT).get("accessToken").asString();

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isNoContent());
        assertUnauthorized(mockMvc.perform(post("/api/v1/auth/logout")));
    }

    @Test
    @DisplayName("Access token không có tác dụng ở trang web; web vẫn chuyển về trang đăng nhập")
    void bearerTokenDoesNotAuthenticateWeb() throws Exception {
        String access = tokens(TestAccounts.STUDENT).get("accessToken").asString();

        mockMvc.perform(get("/student").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("OpenAPI mở công khai, chỉ mô tả /api/**")
    void openApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("/api/v1/auth/login"),
                        containsString("/api/v1/me/password"), containsString("bearerAuth"),
                        not(containsString("/admin/")))));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    private ResultActions login(String phone, String password) throws Exception {
        return postJson("/api/v1/auth/login", body(Map.of("phone", phone, "password", password)));
    }

    private JsonNode tokens(String phone) throws Exception {
        return json(login(phone, PASSWORD).andExpect(status().isOk()));
    }

    private ResultActions changePassword(String access, String current, String next, String confirm)
            throws Exception {
        return mockMvc.perform(post("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, bearer(access))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("currentPassword", current, "newPassword", next, "confirmPassword", confirm))));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertUnauthorized(ResultActions actions) throws Exception {
        JsonNode error = json(actions.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer"))));
        assertError(error, 401, "UNAUTHORIZED", "Phiên đăng nhập đã hết hạn hoặc không hợp lệ, vui lòng đăng nhập lại");
    }

    private static void assertError(JsonNode error, int status, String code, String message) {
        assertThat(error.get("status").asInt()).isEqualTo(status);
        assertThat(error.get("code").asString()).isEqualTo(code);
        assertThat(error.get("message").asString()).isEqualTo(message);
        assertThat(error.get("timestamp").asString()).isNotBlank();
        assertThat(error.get("fieldErrors").isArray()).isTrue();
    }

    private void deactivate(long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);
    }

    private String body(Map<String, String> values) {
        return jsonMapper.writeValueAsString(values);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return json(actions.andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }

    private static Map<String, String> fieldErrors(JsonNode error) {
        Map<String, String> errors = new LinkedHashMap<>();
        error.get("fieldErrors").forEach(node -> errors.put(node.get("field").asString(), node.get("message").asString()));
        return errors;
    }

    private static JwtTokenService tokenService(Instant now, String secret) {
        return new JwtTokenService(JwtTokenService.signingKey(secret), Duration.ofMinutes(15), Duration.ofDays(14),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long lastAuditId() {
        return auditLogRepository.findAll().stream().mapToLong(AuditLog::getId).max().orElse(0);
    }

    private List<AuditLog> auditsAfter(long lastId) {
        return auditLogRepository.findAll().stream().filter(entry -> entry.getId() > lastId)
                .sorted(Comparator.comparing(AuditLog::getId)).toList();
    }
}
