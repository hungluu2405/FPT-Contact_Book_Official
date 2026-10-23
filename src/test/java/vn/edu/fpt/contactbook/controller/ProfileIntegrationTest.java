package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ProfileIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AppUserDetailsService userDetailsService;

    @Test
    @DisplayName("Xem hồ sơ: thông tin, vai trò, số điện thoại không sửa được")
    void viewProfile() throws Exception {
        mockMvc.perform(get("/profile").with(as(TestAccounts.TEACHER_HEAD)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Nguyễn Văn Trưởng"),
                        containsString("0900000002"),
                        containsString("head@fpt.edu.vn"),
                        containsString("Tổ trưởng chuyên môn"),
                        containsString("Số điện thoại do quản trị viên quản lý."),
                        containsString("enctype=\"multipart/form-data\""))));
    }

    @Test
    @DisplayName("Sửa email và địa chỉ; ghi audit UPDATE_PROFILE")
    void updateEmailAndAddress() throws Exception {
        long auditBefore = auditLogRepository.count();

        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).with(csrf())
                        .param("email", "  new.student@fpt.edu.vn ")
                        .param("address", "Hòa Lạc, Hà Nội")
                        .param("phone", "0999999999"))
                .andExpect(redirectedUrl("/profile"));

        User student = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow();
        assertThat(student.getEmail()).isEqualTo("new.student@fpt.edu.vn");
        assertThat(student.getAddress()).isEqualTo("Hòa Lạc, Hà Nội");
        assertThat(student.getPhone()).as("số điện thoại không đổi").isEqualTo(TestAccounts.STUDENT);
        assertThat(auditLogRepository.count()).isEqualTo(auditBefore + 1);
        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditActions.UPDATE_PROFILE);
            assertThat(entry.getEntityId()).isEqualTo(TestAccounts.STUDENT_ID);
            assertThat(entry.getDetail()).isEqualTo("fields=email,address");
        });
    }

    @Test
    @DisplayName("Email trùng người khác (khác hoa thường) bị từ chối")
    void duplicateEmailIsRejected() throws Exception {
        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).with(csrf())
                        .param("email", "HEAD@FPT.EDU.VN")
                        .param("address", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email đã được người khác sử dụng")));

        assertThat(userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getEmail())
                .isEqualTo("student@fpt.edu.vn");
    }

    @Test
    @DisplayName("Giữ email của chính mình (đổi hoa thường) không bị coi là trùng")
    void ownEmailIsAllowed() throws Exception {
        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).with(csrf())
                        .param("email", "Student@FPT.edu.vn"))
                .andExpect(redirectedUrl("/profile"));
    }

    @Test
    @DisplayName("Email rỗng lưu thành NULL; email sai định dạng báo lỗi")
    void blankAndInvalidEmail() throws Exception {
        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).with(csrf()).param("email", "   "))
                .andExpect(redirectedUrl("/profile"));
        assertThat(userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getEmail()).isNull();

        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).with(csrf()).param("email", "khong-hop-le"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email không hợp lệ")));
    }

    @Test
    @DisplayName("Tải ảnh PNG: lưu tên UUID, xem lại được khi đã đăng nhập")
    void uploadPngAvatar() throws Exception {
        mockMvc.perform(multipart("/profile")
                        .file(new MockMultipartFile("avatar", "../../evil name.png", "image/png", PNG))
                        .with(as(TestAccounts.STUDENT)).with(csrf())
                        .param("email", "student@fpt.edu.vn"))
                .andExpect(redirectedUrl("/profile"));

        String avatarUrl = userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getAvatarUrl();
        assertThat(avatarUrl).matches("/avatars/[0-9a-f-]{36}\\.png");

        mockMvc.perform(get(avatarUrl).with(as(TestAccounts.PARENT)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get(avatarUrl)).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Ảnh giả PNG hoặc quá 2 MB bị từ chối, giữ ảnh cũ")
    void rejectsInvalidAvatars() throws Exception {
        mockMvc.perform(multipart("/profile")
                        .file(new MockMultipartFile("avatar", "a.png", "image/png", "GIF89a....".getBytes()))
                        .with(as(TestAccounts.STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ảnh đại diện chỉ nhận JPG hoặc PNG")));

        byte[] tooBig = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);
        mockMvc.perform(multipart("/profile")
                        .file(new MockMultipartFile("avatar", "a.png", "image/png", tooBig))
                        .with(as(TestAccounts.STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ảnh đại diện tối đa 2 MB")));

        assertThat(userRepository.findById(TestAccounts.STUDENT_ID).orElseThrow().getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("Ảnh không tồn tại hoặc tên lạ trả 404")
    void unknownAvatarIsNotFound() throws Exception {
        mockMvc.perform(get("/avatars/00000000-0000-0000-0000-000000000000.png").with(as(TestAccounts.ADMIN)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/avatars/..%2Fapplication.properties").with(as(TestAccounts.ADMIN)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST hồ sơ thiếu CSRF bị 403")
    void updateWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(multipart("/profile").with(as(TestAccounts.STUDENT)).param("email", "x@fpt.edu.vn"))
                .andExpect(status().isForbidden());
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
