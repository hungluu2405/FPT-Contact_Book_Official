package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nội dung trang chủ theo vai trò trên dữ liệu mẫu của script gốc (phân quyền: AccessControlIntegrationTest).
 */
@IntegrationTest
class HomeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserDetailsService userDetailsService;

    @ParameterizedTest
    @ValueSource(strings = {TestAccounts.ADMIN, TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
    @DisplayName("Không trang chủ nào còn thẻ 'Sắp có' của giai đoạn đầu")
    void noComingSoonPlaceholder(String phone) throws Exception {
        String home = switch (phone) {
            case TestAccounts.ADMIN -> "/admin";
            case TestAccounts.TEACHER -> "/teacher";
            case TestAccounts.STUDENT -> "/student";
            default -> "/parent";
        };
        mockMvc.perform(get(home).with(as(phone)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Sắp có"))));
    }

    @Test
    @DisplayName("Giáo viên: lịch dạy hôm nay, đơn chờ duyệt, lớp giảng dạy, thông báo; không có lối tắt quản lý tổ")
    void teacherHome() throws Exception {
        mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER)))
                .andExpect(content().string(allOf(
                        containsString("Lịch dạy hôm nay"),
                        containsString("Đơn chờ duyệt"),
                        containsString("href=\"/teacher/requests\""),
                        containsString("href=\"/teacher/sections\""),
                        containsString("Thông báo mới"),
                        not(containsString("Quản lý tổ chuyên môn")))));
    }

    @Test
    @DisplayName("Tổ trưởng: thêm khối quản lý tổ chuyên môn")
    void headHome() throws Exception {
        mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER_HEAD)))
                .andExpect(content().string(allOf(
                        containsString("Quản lý tổ chuyên môn"),
                        containsString("Tổ Toán – Tin học (TT)"))));
    }

    @Test
    @DisplayName("Học sinh: lịch học hôm nay, lịch thi sắp tới, chuyên cần, thông báo")
    void studentHome() throws Exception {
        mockMvc.perform(get("/student").with(as(TestAccounts.STUDENT)))
                .andExpect(content().string(allOf(
                        containsString("Lịch học hôm nay"),
                        containsString("Lịch thi sắp tới"),
                        containsString("Chuyên cần"),
                        containsString("href=\"/student/schedule\""),
                        containsString("Thông báo mới"))));
    }

    @Test
    @DisplayName("Phụ huynh: thẻ của con đã liên kết, có lối tắt tới trang của đúng con đó")
    void parentHome() throws Exception {
        mockMvc.perform(get("/parent").with(as(TestAccounts.PARENT)))
                .andExpect(content().string(allOf(
                        containsString("Học kỳ I năm học 2026–2027"),
                        containsString("Lê Minh Sinh"),
                        containsString("HS260001"),
                        containsString("href=\"/parent/children/1/schedule\""),
                        containsString("href=\"/parent/children/1/grades\""),
                        containsString("href=\"/parent/children/1/attendance\""))));
    }

    @Test
    @DisplayName("Quản trị: học kỳ hiện tại, đơn chờ xử lý, số liệu tổng quan, lối tắt")
    void adminHome() throws Exception {
        mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(
                        containsString("Học kỳ I năm học 2026–2027"),
                        containsString("Đơn chờ xử lý"),
                        containsString("href=\"/admin/requests\""),
                        containsString("Tài khoản đang hoạt động"),
                        containsString("href=\"/admin/users\""))));
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
