package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nhật ký hệ thống /admin/audit-logs trên FptContactBook_test: chỉ ADMIN, chỉ đọc, bộ lọc theo người thực hiện,
 * hành động, loại đối tượng, khoảng ngày (tính cả hai đầu). Dòng nhật ký của test được ghi với mốc thời gian năm
 * 2001 để không lẫn với dữ liệu mẫu.
 */
@IntegrationTest
class AuditLogIntegrationTest {

    static final String BASE = "/admin/audit-logs";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired UserRepository userRepository;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("Không phải ADMIN (GV, TBM, SV, PH) → 403 cả danh sách và chi tiết; chưa đăng nhập → /login")
    void adminOnly() throws Exception {
        for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.TEACHER_HEAD, TestAccounts.STUDENT,
                TestAccounts.PARENT)) {
            mockMvc.perform(get(BASE).with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/1").with(as(phone))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(BASE)).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Danh sách của ADMIN: có dữ liệu, ô lọc hành động có mã mới; không có nút sửa / xóa")
    void listReadOnly() throws Exception {
        mockMvc.perform(get(BASE).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("LOGIN"), containsString("value=\"LOGIN_FAILED\""),
                        containsString("value=\"EXPORT_REPORT\""), not(containsString(BASE + "/1/edit")),
                        not(containsString(BASE + "/1/delete")), not(containsString("data-confirm")))));
    }

    @Test
    @DisplayName("Không có route ghi: mọi mapping của /admin/audit-logs chỉ là GET; POST/PUT/DELETE → 405")
    void noWriteRoutes() throws Exception {
        List<RequestMappingInfo> mappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().stream().anyMatch(p -> p.startsWith(BASE)))
                .toList();
        assertThat(mappings).isNotEmpty().allSatisfy(info ->
                assertThat(info.getMethodsCondition().getMethods()).isEqualTo(Set.of(RequestMethod.GET)));

        mockMvc.perform(post(BASE).with(csrf()).with(as(TestAccounts.ADMIN))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(BASE + "/1").with(csrf()).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(BASE + "/1").with(csrf()).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Lọc theo hành động, loại đối tượng, người thực hiện (tên hoặc số dạng +84)")
    void filters() throws Exception {
        log(TestAccounts.ADMIN_ID, "TEST_ALPHA", "Grade", "alpha-detail", LocalDateTime.of(2001, 5, 1, 9, 0));
        log(TestAccounts.TEACHER_ID, "TEST_BETA", "Exam", "beta-detail", LocalDateTime.of(2001, 5, 1, 10, 0));

        mockMvc.perform(get(BASE).param("action", "TEST_ALPHA").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(containsString("TEST_ALPHA"), not(containsString("TEST_BETA")))));
        mockMvc.perform(get(BASE).param("entityType", "Exam").param("from", "2001-05-01").param("to", "2001-05-01")
                        .with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(containsString("TEST_BETA"), not(containsString("TEST_ALPHA")))));
        mockMvc.perform(get(BASE).param("keyword", "+84900000003").param("from", "2001-05-01")
                        .param("to", "2001-05-01").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(containsString("TEST_BETA"), not(containsString("TEST_ALPHA")))));
        String adminName = userRepository.findById(TestAccounts.ADMIN_ID).orElseThrow().getFullName();
        mockMvc.perform(get(BASE).param("keyword", adminName).param("from", "2001-05-01").param("to", "2001-05-01")
                        .with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(containsString("TEST_ALPHA"), not(containsString("TEST_BETA")))));
    }

    @Test
    @DisplayName("Khoảng ngày tính cả hai đầu: 23:59:59 của đến ngày có, 00:00 ngày sau không")
    void dateBoundaries() throws Exception {
        log(TestAccounts.ADMIN_ID, "TEST_BEFORE", null, null, LocalDateTime.of(2001, 4, 30, 23, 59, 59));
        log(TestAccounts.ADMIN_ID, "TEST_FIRST", null, null, LocalDateTime.of(2001, 5, 1, 0, 0));
        log(TestAccounts.ADMIN_ID, "TEST_LAST", null, null, LocalDateTime.of(2001, 5, 2, 23, 59, 59));
        log(TestAccounts.ADMIN_ID, "TEST_AFTER", null, null, LocalDateTime.of(2001, 5, 3, 0, 0));

        mockMvc.perform(get(BASE).param("from", "2001-05-01").param("to", "2001-05-02").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(containsString("TEST_FIRST"), containsString("TEST_LAST"),
                        not(containsString("TEST_BEFORE")), not(containsString("TEST_AFTER")))));
    }

    @Test
    @DisplayName("Đến ngày trước từ ngày: báo lỗi cạnh ô, không lỗi 500; ngày sai định dạng cũng báo lỗi")
    void invalidRange() throws Exception {
        mockMvc.perform(get(BASE).param("from", "2001-05-02").param("to", "2001-05-01").with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đến ngày phải bằng hoặc sau từ ngày")));
        mockMvc.perform(get(BASE).param("from", "khong-phai-ngay").with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ngày không hợp lệ")));
    }

    @Test
    @DisplayName("Chi tiết hiện detail bằng th:text (HTML bị escape); dòng không gắn tài khoản; id không có → 404")
    void detail() throws Exception {
        AuditLog entry = log(null, "TEST_DETAIL", null, "<b>reason</b>\nline 2", LocalDateTime.of(2001, 5, 1, 9, 0));

        mockMvc.perform(get(BASE + "/" + entry.getId()).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("&lt;b&gt;reason&lt;/b&gt;"),
                        not(containsString("<b>reason</b>")), containsString("Không gắn tài khoản"))));
        mockMvc.perform(get(BASE + "/999999999").with(as(TestAccounts.ADMIN))).andExpect(status().isNotFound());
    }

    private AuditLog log(Long userId, String action, String entityType, String detail, LocalDateTime createdAt) {
        AuditLog entry = new AuditLog();
        entry.setUser(userId == null ? null : userRepository.getReferenceById(userId));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setDetail(detail);
        entry.setIpAddress("127.0.0.1");
        entry.setCreatedAt(createdAt);
        return auditLogRepository.saveAndFlush(entry);
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
