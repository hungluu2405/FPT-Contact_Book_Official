package vn.edu.fpt.contactbook.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.support.ApiClient;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.ApiClient.fieldNames;

/**
 * API của tổ trưởng chuyên môn (giai đoạn 10e-1) và thông báo đã gửi. Dữ liệu mẫu: 0900000002 là tổ trưởng tổ Toán –
 * Tin học (departments.id 1, mã TT); tổ có 4 giáo viên: users.id 2 (GV001, tổ trưởng), 3 (GV002), 16 (GV003), 17
 * (GV004). Giáo viên tổ khác được tạo trong test (rollback).
 */
@IntegrationTest
class HeadApiIntegrationTest {

    static final String API = "/api/v1/head";
    static final byte[] PDF = "%PDF-1.7\nbien ban hop to".getBytes(StandardCharsets.US_ASCII);
    static final long OTHER_DEPARTMENT_ID = 38L;

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired UserRepository userRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired PasswordEncoder passwordEncoder;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("Chỉ tổ trưởng gọi được /head/**; giáo viên thường, HS, PH, ADMIN 403; không token 401")
        void roles() throws Exception {
            api.perform(get(API + "/departments"), TestAccounts.TEACHER_HEAD).andExpect(status().isOk());
            for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT,
                    TestAccounts.ADMIN)) {
                JsonNode error = api.json(api.perform(get(API + "/teachers"), phone)
                        .andExpect(status().isForbidden()));
                assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
            }
            mockMvc.perform(get(API + "/departments")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Tổ và giáo viên")
    class Teachers {

        @Test
        @DisplayName("Tổ mình làm tổ trưởng kèm số giáo viên")
        void departments() throws Exception {
            JsonNode list = api.json(api.perform(get(API + "/departments"), TestAccounts.TEACHER_HEAD)
                    .andExpect(status().isOk()));

            assertThat(list).hasSize(1);
            assertThat(fieldNames(list.get(0))).containsExactly("id", "code", "name", "teacherCount");
            assertThat(list.get(0).get("code").asString()).isEqualTo("TT");
            assertThat(list.get(0).get("teacherCount").asLong()).isEqualTo(4);
        }

        @Test
        @DisplayName("Danh sách giáo viên trong tổ: theo tên, tìm theo mã, không có giáo viên tổ khác")
        void list() throws Exception {
            createTeacher("0955000001", "GV900", OTHER_DEPARTMENT_ID);

            JsonNode page = api.json(api.perform(get(API + "/teachers").param("departmentId", "1"),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isOk()));

            assertThat(page.get("totalElements").asLong()).isEqualTo(4);
            JsonNode first = page.get("items").get(0);
            assertThat(fieldNames(first)).containsExactly("userId", "teacherCode", "fullName", "phone", "active",
                    "head");
            List<String> codes = new ArrayList<>();
            page.get("items").forEach(item -> codes.add(item.get("teacherCode").asString()));
            assertThat(codes).containsExactlyInAnyOrder("GV001", "GV002", "GV003", "GV004");
            page.get("items").forEach(item -> assertThat(item.get("head").asBoolean())
                    .isEqualTo(item.get("userId").asLong() == TestAccounts.TEACHER_HEAD_ID));

            JsonNode found = api.json(api.perform(get(API + "/teachers").param("q", "gv002"),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isOk()));
            assertThat(found.get("items")).hasSize(1);
            assertThat(found.get("items").get(0).get("userId").asLong()).isEqualTo(TestAccounts.TEACHER_ID);

            JsonNode other = api.json(api.perform(get(API + "/teachers")
                    .param("departmentId", String.valueOf(OTHER_DEPARTMENT_ID)), TestAccounts.TEACHER_HEAD)
                    .andExpect(status().isOk()));
            assertThat(other.get("items")).isEmpty();
        }

        @Test
        @DisplayName("Chi tiết giáo viên trong tổ, không lộ mật khẩu băm hay hồ sơ thừa")
        void detail() throws Exception {
            JsonNode teacher = api.json(api.perform(get(API + "/teachers/" + TestAccounts.TEACHER_ID),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isOk()));

            assertThat(fieldNames(teacher)).containsExactly("userId", "teacherCode", "fullName", "academicTitle",
                    "departmentName", "phone", "email", "active", "lastLoginAt", "roles");
            assertThat(teacher.get("teacherCode").asString()).isEqualTo("GV002");
            assertThat(teacher.get("phone").asString()).isEqualTo(TestAccounts.TEACHER);
            assertThat(teacher.get("departmentName").asString()).isEqualTo("Tổ Toán – Tin học");
            assertThat(teacher.get("roles").get(0).asString()).isEqualTo("TEACHER");
        }

        @Test
        @DisplayName("Giáo viên tổ khác hoặc người không phải giáo viên: 403")
        void outOfScope() throws Exception {
            long outsider = createTeacher("0955000002", "GV901", OTHER_DEPARTMENT_ID);

            for (long userId : List.of(outsider, TestAccounts.STUDENT_ID, 999_999L)) {
                api.perform(get(API + "/teachers/" + userId), TestAccounts.TEACHER_HEAD)
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Nested
    @DisplayName("Gửi thông báo nội bộ")
    class Send {

        @Test
        @DisplayName("Gửi cả tổ: 3 giáo viên còn lại nhận, không có người gửi, kèm tệp")
        void toDepartment() throws Exception {
            JsonNode sent = api.json(api.perform(notification("Họp tổ chuyên môn tháng 11")
                    .file(new MockMultipartFile("files", "bien-ban.pdf", "application/pdf", PDF))
                    .param("departmentId", "1"), TestAccounts.TEACHER_HEAD).andExpect(status().isCreated()));

            assertThat(sent.get("targetType").asString()).isEqualTo("DEPARTMENT");
            assertThat(sent.get("targetLabel").asString()).isEqualTo("TT - Tổ Toán – Tin học");
            assertThat(sent.get("recipientCount").asLong()).isEqualTo(3);
            assertThat(sent.get("readCount").asLong()).isZero();
            assertThat(sent.get("files").get(0).get("fileName").asString()).isEqualTo("bien-ban.pdf");
            List<String> names = new ArrayList<>();
            sent.get("recipients").get("items").forEach(row -> names.add(row.get("fullName").asString()));
            assertThat(names).hasSize(3).doesNotContain(userRepository.findById(TestAccounts.TEACHER_HEAD_ID)
                    .orElseThrow().getFullName());

            JsonNode inbox = api.json(api.perform(get("/api/v1/notifications"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(inbox.get("items").get(0).get("id").asLong()).isEqualTo(sent.get("id").asLong());
        }

        @Test
        @DisplayName("Gửi riêng một giáo viên trong tổ")
        void toOneTeacher() throws Exception {
            JsonNode sent = api.json(api.perform(notification("Nhắc nộp đề kiểm tra")
                    .param("teacherUserId", String.valueOf(TestAccounts.TEACHER_ID)), TestAccounts.TEACHER_HEAD)
                    .andExpect(status().isCreated()));

            assertThat(sent.get("targetType").asString()).isEqualTo("USER");
            assertThat(sent.get("recipientCount").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("Thiếu tiêu đề, nội dung, nơi nhận: lỗi theo ô; gửi cho chính mình: không có người nhận")
        void validation() throws Exception {
            JsonNode missing = api.json(api.perform(multipart(API + "/notifications").param("priority", "NORMAL"),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isBadRequest()));
            List<String> fields = new ArrayList<>();
            missing.get("fieldErrors").forEach(error -> fields.add(error.get("field").asString()));
            assertThat(fields).contains("title", "content", "departmentId");

            JsonNode self = api.json(api.perform(notification("Tự nhắc")
                    .param("teacherUserId", String.valueOf(TestAccounts.TEACHER_HEAD_ID)), TestAccounts.TEACHER_HEAD)
                    .andExpect(status().isBadRequest()));
            assertThat(self.get("message").asString())
                    .isEqualTo("Phạm vi đã chọn không có người nhận nào (ngoài chính bạn)");
        }

        @Test
        @DisplayName("Tổ khác hoặc giáo viên tổ khác: 403")
        void outOfScope() throws Exception {
            long outsider = createTeacher("0955000003", "GV902", OTHER_DEPARTMENT_ID);

            api.perform(notification("Sai tổ").param("departmentId", String.valueOf(OTHER_DEPARTMENT_ID)),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isForbidden());
            api.perform(notification("Sai người").param("teacherUserId", String.valueOf(outsider)),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Thông báo đã gửi")
    class Sent {

        @Test
        @DisplayName("Danh sách và chi tiết cập nhật số đã đọc; lọc người đã đọc")
        void readStats() throws Exception {
            long id = sendToDepartment();

            JsonNode list = api.json(api.perform(get("/api/v1/notifications/sent"), TestAccounts.TEACHER_HEAD)
                    .andExpect(status().isOk()));
            JsonNode row = list.get("items").get(0);
            assertThat(fieldNames(row)).containsExactly("id", "title", "priority", "targetType", "createdAt",
                    "recipientCount", "readCount");
            assertThat(row.get("id").asLong()).isEqualTo(id);
            assertThat(row.get("readCount").asLong()).isZero();

            api.perform(get("/api/v1/notifications/" + id), TestAccounts.TEACHER).andExpect(status().isOk());

            JsonNode detail = api.json(api.perform(get("/api/v1/notifications/sent/" + id),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isOk()));
            assertThat(detail.get("readCount").asLong()).isEqualTo(1);
            JsonNode read = api.json(api.perform(get("/api/v1/notifications/sent/" + id).param("read", "true"),
                    TestAccounts.TEACHER_HEAD).andExpect(status().isOk()));
            assertThat(read.get("recipients").get("items")).hasSize(1);
            assertThat(read.get("recipients").get("items").get(0).get("read").asBoolean()).isTrue();
            assertThat(read.get("recipients").get("items").get(0).get("readAt").isNull()).isFalse();
        }

        @Test
        @DisplayName("Người nhận xem thống kê: 403 và không bị đánh dấu đã đọc")
        void recipientCannotSeeStats() throws Exception {
            long id = sendToDepartment();

            api.perform(get("/api/v1/notifications/sent/" + id), TestAccounts.TEACHER)
                    .andExpect(status().isForbidden());

            JsonNode inbox = api.json(api.perform(get("/api/v1/notifications"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(inbox.get("items").get(0).get("read").asBoolean()).isFalse();
        }
    }

    private MockMultipartHttpServletRequestBuilder notification(String title) {
        return (MockMultipartHttpServletRequestBuilder) multipart(API + "/notifications")
                .param("title", title)
                .param("content", "Kính gửi các thầy cô trong tổ.")
                .param("priority", "IMPORTANT");
    }

    private long sendToDepartment() throws Exception {
        return api.json(api.perform(notification("Lịch dự giờ").param("departmentId", "1"),
                TestAccounts.TEACHER_HEAD).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createTeacher(String phone, String code, long departmentId) {
        User user = new User();
        user.setPhone(phone);
        user.setFullName("GV tổ khác " + code);
        user.setPasswordHash(passwordEncoder.encode(TestAccounts.PASSWORD));
        userRepository.save(user);
        Department department = departmentRepository.findById(departmentId).orElseThrow();
        Teacher teacher = new Teacher();
        teacher.setUser(user);
        teacher.setTeacherCode(code);
        teacher.setDepartment(department);
        teacherRepository.saveAndFlush(teacher);
        return user.getId();
    }
}
