package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.Notification;
import vn.edu.fpt.contactbook.model.NotificationRecipient;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.NotificationFileRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.repository.NotificationRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thông báo trên FptContactBook_test. Dữ liệu mẫu: lớp 1 (GV 0900000003, chỉ có SE190001 = users.id 4, phụ huynh
 * 0900000005 = users.id 5 liên kết); lớp 27 của GV khác. Thông báo mẫu 16 (toàn trường) và 17 (lớp PRN211) đã có;
 * SE180101 (0933112233) không học lớp 1.
 */
@IntegrationTest
class NotificationIntegrationTest {

    static final String OUTSIDER = "0933112233";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationRecipientRepository recipientRepository;
    @Autowired NotificationFileRepository notificationFileRepository;
    @Autowired UserRepository userRepository;

    @Nested
    @DisplayName("Gửi và đọc")
    class SendAndRead {

        @Test
        @DisplayName("GV gửi lớp mình: SV và PH nhận, chuông đúng số chưa đọc, thống kê đã đọc khớp")
        void teacherSendsToOwnSection() throws Exception {
            long studentUnread = recipientRepository.countUnread(TestAccounts.STUDENT_ID);
            long parentUnread = recipientRepository.countUnread(TestAccounts.PARENT_ID);
            mockMvc.perform(get("/notifications/new").with(as(TestAccounts.TEACHER))).andExpect(status().isOk());

            Long id = send(sectionForm(1L).with(as(TestAccounts.TEACHER)));

            assertThat(recipientIds(id)).containsExactlyInAnyOrder(TestAccounts.STUDENT_ID, TestAccounts.PARENT_ID);
            assertThat(recipientRepository.countUnread(TestAccounts.STUDENT_ID)).isEqualTo(studentUnread + 1);
            assertThat(recipientRepository.countUnread(TestAccounts.PARENT_ID)).isEqualTo(parentUnread + 1);
            mockMvc.perform(get("/notifications").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Lịch kiểm tra giữa kỳ"),
                            containsString("notification-unread"),
                            containsString("Thông báo, " + (studentUnread + 1) + " chưa đọc"))));

            mockMvc.perform(get("/notifications/" + id).with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ôn tập chương 1–3")));
            assertThat(recipientRepository.countUnread(TestAccounts.STUDENT_ID)).isEqualTo(studentUnread);

            mockMvc.perform(get("/notifications/" + id).with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(containsString("1 / 2 người (50%)")));
            mockMvc.perform(get("/notifications/sent").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(allOf(containsString("Lịch kiểm tra giữa kỳ"),
                            containsString("1 / 2 người (50%)"))));
        }

        @Test
        @DisplayName("Tài khoản bị khóa không nhận thông báo")
        void lockedAccountExcluded() throws Exception {
            User parent = userRepository.findById(TestAccounts.PARENT_ID).orElseThrow();
            parent.setActive(false);
            userRepository.saveAndFlush(parent);

            Long id = send(sectionForm(1L).with(as(TestAccounts.TEACHER)));

            assertThat(recipientIds(id)).containsExactly(TestAccounts.STUDENT_ID);
        }

        @Test
        @DisplayName("Đánh dấu tất cả đã đọc")
        void markAllRead() throws Exception {
            send(sectionForm(1L).with(as(TestAccounts.TEACHER)));

            mockMvc.perform(post("/notifications/read-all").with(csrf()).with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/notifications"));
            assertThat(recipientRepository.countUnread(TestAccounts.PARENT_ID)).isZero();
        }

        @Test
        @DisplayName("Tệp đính kèm: người nhận tải được (attachment); người ngoài mở thông báo hoặc tải tệp → 403")
        void attachmentAccess() throws Exception {
            byte[] pdf = "%PDF-1.7\nnoi dung".getBytes(StandardCharsets.US_ASCII);
            Long id = send(sectionForm(1L)
                    .file(new MockMultipartFile("files", "de-cuong.pdf", "application/pdf", pdf))
                    .with(as(TestAccounts.TEACHER)));
            Long fileId = notificationFileRepository.findByIdNotificationIdOrderByIdFileIdAsc(id).getFirst()
                    .getFile().getId();
            String url = "/notifications/" + id + "/files/" + fileId;

            mockMvc.perform(get(url).with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(content().bytes(pdf));
            mockMvc.perform(get(url).with(as(TestAccounts.ADMIN))).andExpect(status().isOk());
            mockMvc.perform(get(url).with(as(OUTSIDER))).andExpect(status().isForbidden());
            mockMvc.perform(get("/notifications/" + id).with(as(OUTSIDER))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tệp sai loại: báo lỗi trên form, không gửi")
        void invalidAttachment() throws Exception {
            long before = notificationRepository.count();

            mockMvc.perform(sectionForm(1L)
                            .file(new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[]{1}))
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("chỉ nhận PDF, JPG, PNG, DOCX, XLSX")));
            assertThat(notificationRepository.count()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("GV gửi lớp của GV khác (sửa form) → 403")
        void teacherOtherSection() throws Exception {
            mockMvc.perform(sectionForm(27L).with(as(TestAccounts.TEACHER))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STUDENT/PARENT vào trang gửi hoặc gửi → 403")
        void studentParentCannotSend() throws Exception {
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.PARENT)) {
                mockMvc.perform(get("/notifications/new").with(as(phone))).andExpect(status().isForbidden());
                mockMvc.perform(sectionForm(1L).with(as(phone))).andExpect(status().isForbidden());
                mockMvc.perform(get("/notifications/sent").with(as(phone))).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Chưa đăng nhập → /login")
        void anonymous() throws Exception {
            for (String url : List.of("/notifications", "/notifications/new", "/notifications/16",
                    "/notifications/sent")) {
                mockMvc.perform(get(url)).andExpect(redirectedUrl("/login"));
            }
        }
    }

    @Nested
    @DisplayName("Thông báo tự động")
    class Automatic {

        @Test
        @DisplayName("Hủy buổi học → SV và PH của lớp nhận thông báo, người gửi là người hủy")
        void sessionCancelled() throws Exception {
            mockMvc.perform(post("/admin/sections/1/sessions/1/cancel").with(csrf()).with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/1"));

            Notification latest = latestFor(TestAccounts.STUDENT_ID);
            assertThat(latest.getTitle()).contains("Hủy buổi 1 ngày 08/09/2026");
            assertThat(latest.getSender().getId()).isEqualTo(TestAccounts.ADMIN_ID);
            assertThat(recipientIds(latest.getId()))
                    .containsExactlyInAnyOrder(TestAccounts.STUDENT_ID, TestAccounts.PARENT_ID);
        }

        @Test
        @DisplayName("Công bố điểm → SV và PH nhận thông báo")
        void gradesPublished() throws Exception {
            mockMvc.perform(post("/teacher/sections/1/grades").with(csrf()).param("scores[1_1]", "8")
                    .with(as(TestAccounts.TEACHER))).andExpect(status().is3xxRedirection());
            mockMvc.perform(post("/admin/sections/1/grades/publish").with(csrf())
                    .with(as(TestAccounts.TEACHER_HEAD))).andExpect(redirectedUrl("/admin/sections/1/grades"));

            Notification latest = latestFor(TestAccounts.PARENT_ID);
            assertThat(latest.getTitle()).isEqualTo("[PRJ301-SE1901-FA26] Đã công bố điểm");
            assertThat(latest.getSender().getId()).isEqualTo(TestAccounts.TEACHER_HEAD_ID);
            assertThat(recipientIds(latest.getId()))
                    .containsExactlyInAnyOrder(TestAccounts.STUDENT_ID, TestAccounts.PARENT_ID);
        }
    }

    private MockMultipartHttpServletRequestBuilder sectionForm(Long sectionId) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/notifications/new").with(csrf())
                .param("title", "Lịch kiểm tra giữa kỳ")
                .param("content", "Ôn tập chương 1–3.\nMang theo thẻ sinh viên.")
                .param("priority", "IMPORTANT")
                .param("targetType", "CLASS_SECTION")
                .param("sectionId", sectionId.toString());
    }

    /** Gửi và trả id thông báo vừa tạo (từ URL chuyển hướng). */
    private Long send(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(redirectedUrlPattern("/notifications/*")).andReturn();
        String url = result.getResponse().getRedirectedUrl();
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
    }

    private List<Long> recipientIds(Long notificationId) {
        return recipientRepository.findRecipients(notificationId, null, PageRequest.of(0, 50)).stream()
                .map(r -> r.getUser().getId()).toList();
    }

    private Notification latestFor(Long userId) {
        NotificationRecipient row = recipientRepository.findInbox(userId, PageRequest.of(0, 1)).getContent()
                .getFirst();
        return row.getNotification();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
