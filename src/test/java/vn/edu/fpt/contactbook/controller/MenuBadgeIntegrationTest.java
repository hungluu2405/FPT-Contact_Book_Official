package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.MessageService;
import vn.edu.fpt.contactbook.service.NotificationService;
import vn.edu.fpt.contactbook.service.RequestService;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Số đếm trên menu (thông báo, tin nhắn chưa đọc, đơn chờ duyệt) chỉ được tính khi trả về một trang đầy đủ. Ảnh, tải
 * tệp, xuất Excel, mảnh HTML làm mới tin nhắn và POST chuyển hướng không được chạy truy vấn đếm (review 8a, P1).
 */
@IntegrationTest
class MenuBadgeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @MockitoSpyBean NotificationService notificationService;
    @MockitoSpyBean MessageService messageService;
    @MockitoSpyBean RequestService requestService;

    @BeforeEach
    void reset() {
        clearInvocations(notificationService, messageService, requestService);
    }

    @Test
    @DisplayName("Trang đầy đủ: đếm đúng một lần mỗi số, và Model có các số đếm")
    void fullPage() throws Exception {
        mockMvc.perform(get("/notifications").with(as(TestAccounts.TEACHER_HEAD)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("unreadNotifications", "unreadMessages",
                        "pendingTeacherRequests"));
        verify(notificationService, times(1)).unreadCount(TestAccounts.TEACHER_HEAD_ID);
        verify(messageService, times(1)).unreadCount(TestAccounts.TEACHER_HEAD_ID);
        verify(requestService, times(1)).pendingForTeacher(any());

        mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                .andExpect(model().attributeExists("unreadNotifications", "pendingAdminRequests"))
                .andExpect(model().attributeDoesNotExist("unreadMessages", "pendingTeacherRequests"));
        verify(requestService, times(1)).pendingForAdmin();
    }

    @Test
    @DisplayName("Ảnh đại diện, xuất Excel, POST chuyển hướng: không đếm")
    void nonPageResponses() throws Exception {
        mockMvc.perform(get("/avatars/00000000-0000-0000-0000-000000000000.png").with(as(TestAccounts.STUDENT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/reports/attendance/export").param("sectionId", "1").with(as(TestAccounts.TEACHER)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/notifications/read-all").with(csrf()).with(as(TestAccounts.STUDENT)))
                .andExpect(status().is3xxRedirection());

        assertNoCounts();
    }

    @Test
    @DisplayName("Mảnh HTML làm mới tin nhắn (gọi 10 giây/lần): không đếm")
    void messageFragment() throws Exception {
        String url = mockMvc.perform(post("/messages/with/{id}", TestAccounts.TEACHER_ID).with(csrf())
                        .param("content", "Chào thầy").with(as(TestAccounts.STUDENT)))
                .andExpect(redirectedUrlPattern("/messages/*"))
                .andReturn().getResponse().getRedirectedUrl();
        clearInvocations(notificationService, messageService, requestService);

        mockMvc.perform(get(url + "/newer").param("after", "0").with(as(TestAccounts.STUDENT)))
                .andExpect(status().isOk());

        assertNoCounts();
    }

    private void assertNoCounts() {
        verify(notificationService, never()).unreadCount(anyLong());
        verify(messageService, never()).unreadCount(anyLong());
        verify(requestService, never()).pendingForTeacher(any());
        verify(requestService, never()).pendingForAdmin();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
