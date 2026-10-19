package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Message;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ConversationRepository;
import vn.edu.fpt.contactbook.repository.MessageRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tin nhắn trên FptContactBook_test. Dữ liệu mẫu: phụ huynh 0900000005 (users.id 5) là cha của SE190001
 * (0900000004, users.id 4), sinh viên này học lớp học phần 1 (FA26, học kỳ hiện tại) do GV 0900000003 (users.id 3,
 * "Trần Thị Giáo") dạy. GV 0900000002 (users.id 2, "Nguyễn Văn Trưởng") không dạy SE190001.
 */
@IntegrationTest
class MessageIntegrationTest {

    static final String UNREAD_BADGE = "id=\"unread-messages\"";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired MessageRepository messageRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("Ai nhắn cho ai")
    class Rules {

        @Test
        @DisplayName("PH nhắn được cho GV đang dạy con: danh bạ có GV đó, gửi tạo một hội thoại, gửi tiếp dùng lại")
        void parentToTeacherOfChild() throws Exception {
            mockMvc.perform(get("/messages/contacts").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Trần Thị Giáo"),
                            not(containsString("Nguyễn Văn Trưởng")))));
            mockMvc.perform(get("/messages/with/{id}", TestAccounts.TEACHER_ID).with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk());

            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Chào cô, cháu dạo này học thế nào ạ?");

            assertThat(conversationRepository.findDirectIds(TestAccounts.PARENT_ID, TestAccounts.TEACHER_ID))
                    .containsExactly(id);
            assertThat(sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin thứ hai")).isEqualTo(id);
            mockMvc.perform(get("/messages/with/{id}", TestAccounts.TEACHER_ID).with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/messages/" + id));
            mockMvc.perform(get("/messages/{id}", id).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Chào cô, cháu dạo này học thế nào ạ?"),
                            containsString("Lê Văn Phụ"))));
        }

        @Test
        @DisplayName("PH nhắn cho GV không dạy con: 403 ở cả trang soạn và khi gửi, không tạo hội thoại")
        void parentToUnrelatedTeacher() throws Exception {
            mockMvc.perform(get("/messages/with/{id}", TestAccounts.TEACHER_HEAD_ID).with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/messages/with/{id}", TestAccounts.TEACHER_HEAD_ID).with(as(TestAccounts.PARENT))
                            .with(csrf()).param("content", "Chào thầy"))
                    .andExpect(status().isForbidden());

            assertThat(conversationRepository.findDirectIds(TestAccounts.PARENT_ID, TestAccounts.TEACHER_HEAD_ID))
                    .isEmpty();
        }

        @Test
        @DisplayName("Hết học kỳ: hội thoại cũ vẫn đọc được nhưng không gửi được tin mới")
        void relationEnded() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin trước khi hết học kỳ");
            Semester fa26 = semesterRepository.findById(1L).orElseThrow();
            fa26.setCurrent(false);
            semesterRepository.flush();

            mockMvc.perform(get("/messages/{id}", id).with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Tin trước khi hết học kỳ"),
                            containsString("Bạn không còn được nhắn tin cho người này"))));
            mockMvc.perform(post("/messages/{id}/send", id).with(as(TestAccounts.PARENT)).with(csrf())
                            .param("content", "Tin sau khi hết học kỳ"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Bạn không còn được nhắn tin cho người này")));
            assertThat(messages(id)).extracting(Message::getContent).containsExactly("Tin trước khi hết học kỳ");
        }
    }

    @Nested
    @DisplayName("Truy cập")
    class Access {

        @Test
        @DisplayName("Người ngoài hội thoại mở, xem mảnh, gửi: 403; hội thoại không tồn tại cũng 403")
        void outsider() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Riêng tư");

            for (String outsider : List.of(TestAccounts.STUDENT, TestAccounts.TEACHER_HEAD)) {
                mockMvc.perform(get("/messages/{id}", id).with(as(outsider))).andExpect(status().isForbidden());
                mockMvc.perform(get("/messages/{id}/newer", id).with(as(outsider)))
                        .andExpect(status().isForbidden());
                mockMvc.perform(post("/messages/{id}/send", id).with(as(outsider)).with(csrf())
                        .param("content", "x")).andExpect(status().isForbidden());
            }
            mockMvc.perform(get("/messages/{id}", 999_999L).with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN vào /messages: 403")
        void adminForbidden() throws Exception {
            mockMvc.perform(get("/messages").with(as(TestAccounts.ADMIN))).andExpect(status().isForbidden());
            mockMvc.perform(get("/messages/contacts").with(as(TestAccounts.ADMIN))).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(not(containsString("href=\"/messages\""))));
        }

        @Test
        @DisplayName("Đúng vai trò vào /messages: 200 và có mục menu Tin nhắn")
        void allowedRoles() throws Exception {
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.PARENT, TestAccounts.TEACHER,
                    TestAccounts.TEACHER_HEAD)) {
                mockMvc.perform(get("/messages").with(as(phone)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("href=\"/messages\"")));
            }
        }

        @Test
        @DisplayName("Chưa đăng nhập: chuyển tới trang đăng nhập")
        void anonymous() throws Exception {
            mockMvc.perform(get("/messages")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/messages/1")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/messages/1/newer")).andExpect(redirectedUrl("/login"));
        }
    }

    @Nested
    @DisplayName("Gửi, xóa, chưa đọc")
    class Flow {

        @Test
        @DisplayName("Xóa tin của người khác: 403; xóa tin của mình: is_deleted = 1, dòng còn, hiện 'Tin nhắn đã bị xóa'")
        void delete() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Gửi nhầm");
            Message message = messages(id).getFirst();

            mockMvc.perform(post("/messages/{c}/messages/{m}/delete", id, message.getId())
                            .with(as(TestAccounts.TEACHER)).with(csrf()))
                    .andExpect(status().isForbidden());
            assertThat(messageRepository.findById(message.getId()).orElseThrow().isDeleted()).isFalse();

            mockMvc.perform(post("/messages/{c}/messages/{m}/delete", id, message.getId())
                            .with(as(TestAccounts.PARENT)).with(csrf()))
                    .andExpect(redirectedUrl("/messages/" + id));

            Message deleted = messageRepository.findById(message.getId()).orElseThrow();
            assertThat(deleted.isDeleted()).isTrue();
            assertThat(deleted.getContent()).isEqualTo("Gửi nhầm");
            mockMvc.perform(get("/messages/{id}", id).with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(allOf(containsString("Tin nhắn đã bị xóa"),
                            not(containsString("Gửi nhầm")))));
            List<AuditLog> audits = auditLogRepository.findAll().stream()
                    .filter(a -> AuditActions.DELETE_MESSAGE.equals(a.getAction())).toList();
            assertThat(audits).singleElement().extracting(AuditLog::getEntityId).isEqualTo(message.getId());
        }

        @Test
        @DisplayName("Số tin chưa đọc đúng trên menu và danh sách; mở hội thoại thì về 0")
        void unread() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin 1");
            sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin 2");

            mockMvc.perform(get("/notifications").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(containsString(UNREAD_BADGE + ">2</span>")));
            mockMvc.perform(get("/messages").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(containsString("aria-label=\"2 tin chưa đọc\"")));
            mockMvc.perform(get("/notifications").with(as(TestAccounts.PARENT)))
                    .andExpect(content().string(not(containsString(UNREAD_BADGE))));

            // Ngay trang vừa mở hội thoại, số trên menu đã về 0 (không phải đến trang kế tiếp).
            mockMvc.perform(get("/messages/{id}", id).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(UNREAD_BADGE))));

            mockMvc.perform(get("/messages").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(allOf(not(containsString(UNREAD_BADGE)),
                            not(containsString("tin chưa đọc\"")))));
        }

        @Test
        @DisplayName("Mảnh làm mới trả HTML các tin mới hơn mốc, không phải JSON, không có khung trang")
        void newerFragment() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin cũ");
            Long firstId = messages(id).getFirst().getId();
            sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin mới");

            mockMvc.perform(get("/messages/{id}/newer", id).param("after", firstId.toString())
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(allOf(containsString("Tin mới"), not(containsString("Tin cũ")),
                            not(containsString("<html")), containsString("chat-message"))));
        }

        @Test
        @DisplayName("Nội dung được escape (th:text): thẻ script hiện dạng chữ")
        void escapesContent() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "<script>alert(1)</script>");

            mockMvc.perform(get("/messages/{id}", id).with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(allOf(containsString("&lt;script&gt;alert(1)&lt;/script&gt;"),
                            not(containsString("<script>alert(1)</script>")))));
        }

        @Test
        @DisplayName("Rỗng hoặc quá 2000 ký tự: báo lỗi cạnh ô, không lưu; 2000 ký tự kèm khoảng trắng hai đầu: hợp lệ")
        void invalidContent() throws Exception {
            Long id = sendTo(TestAccounts.PARENT, TestAccounts.TEACHER_ID, "Tin hợp lệ");

            mockMvc.perform(post("/messages/{id}/send", id).with(as(TestAccounts.PARENT)).with(csrf())
                            .param("content", "   "))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vui lòng nhập nội dung tin nhắn")));
            mockMvc.perform(post("/messages/{id}/send", id).with(as(TestAccounts.PARENT)).with(csrf())
                            .param("content", "a".repeat(2001)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Tin nhắn tối đa 2000 ký tự")));
            assertThat(messages(id)).hasSize(1);

            mockMvc.perform(post("/messages/{id}/send", id).with(as(TestAccounts.PARENT)).with(csrf())
                            .param("content", "  " + "b".repeat(2000) + "\n"))
                    .andExpect(redirectedUrl("/messages/" + id));
            assertThat(messages(id)).last().extracting(Message::getContent).isEqualTo("b".repeat(2000));
        }
    }

    private Long sendTo(String phone, long otherUserId, String content) throws Exception {
        String url = mockMvc.perform(post("/messages/with/{id}", otherUserId).with(as(phone)).with(csrf())
                        .param("content", content))
                .andExpect(redirectedUrlPattern("/messages/*"))
                .andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
    }

    private List<Message> messages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, 100)).reversed();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
