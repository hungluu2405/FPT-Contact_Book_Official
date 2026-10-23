package vn.edu.fpt.contactbook.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.repository.NotificationFileRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.support.ApiClient;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
import static vn.edu.fpt.contactbook.support.ApiClient.fieldNames;

/**
 * API của sinh viên và phụ huynh (giai đoạn 9c): đúng vai trò, phạm vi dữ liệu do service kiểm tra, tên trường JSON
 * đúng hợp đồng docs/API.md. Dữ liệu mẫu: SV 0900000004 là students.id 1 (HS260001, lớp TOAN10 = class_sections.id 1);
 * PH 0900000005 chỉ liên kết với students.id 1.
 */
@IntegrationTest
class StudentParentApiIntegrationTest {

    static final String CHILD = "/api/v1/parent/children/1";
    static final String OTHER_CHILD = "/api/v1/parent/children/2";
    /** Chủ nhiệm CLB, không liên quan tới HS260001. */
    static final String OUTSIDER = "0933112233";
    static final byte[] PDF = "%PDF-1.7\ngiay kham benh".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired UserRequestRepository requestRepository;
    @Autowired NotificationRecipientRepository recipientRepository;
    @Autowired NotificationFileRepository notificationFileRepository;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("API sinh viên chỉ cho STUDENT, API phụ huynh chỉ cho PARENT; sai vai trò 403 JSON")
        void rolesAreEnforced() throws Exception {
            for (String phone : List.of(TestAccounts.PARENT, TestAccounts.TEACHER, TestAccounts.ADMIN)) {
                JsonNode error = api.json(api.perform(get("/api/v1/student/grades"), phone)
                        .andExpect(status().isForbidden()));
                assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
            }
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.TEACHER)) {
                api.perform(get("/api/v1/parent/children"), phone).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Không có token: 401 ở mọi nhóm API mới")
        void anonymousIsUnauthorized() throws Exception {
            for (String url : List.of("/api/v1/student/schedule/week", CHILD + "/grades", "/api/v1/notifications",
                    "/api/v1/semesters", "/api/v1/me/profile", "/api/v1/requests/1/files/1")) {
                mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            }
        }

        @Test
        @DisplayName("Phụ huynh xem con chưa liên kết: 403 ở lịch, điểm, chuyên cần, đơn")
        void parentCannotSeeUnlinkedChild() throws Exception {
            for (String path : List.of("/schedule/week", "/schedule/sessions", "/exams", "/grades", "/attendance",
                    "/attendance/1", "/requests", "/requests/leave-options")) {
                JsonNode error = api.json(api.perform(get(OTHER_CHILD + path), TestAccounts.PARENT)
                        .andExpect(status().isForbidden()));
                assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
            }
            api.perform(leave(OTHER_CHILD + "/requests/leave", AppTime.today(), AppTime.today()), TestAccounts.PARENT)
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Danh sách con của phụ huynh: chỉ HS260001")
        void children() throws Exception {
            JsonNode children = api.json(api.perform(get("/api/v1/parent/children"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));

            assertThat(children).hasSize(1);
            assertThat(children.get(0).get("id").asLong()).isEqualTo(1);
            assertThat(children.get(0).get("code").asString()).isEqualTo("HS260001");
        }
    }

    @Nested
    @DisplayName("Lịch học, lịch thi")
    class Schedule {

        @Test
        @DisplayName("Tuần 07–13/09/2026 của HS260001: TOAN10 ngày 08/09 lúc 07:30–09:45, không có lớp khác; SV và PH như nhau")
        void week() throws Exception {
            for (String[] call : new String[][]{{"/api/v1/student", TestAccounts.STUDENT},
                    {CHILD, TestAccounts.PARENT}}) {
                JsonNode body = api.json(api.perform(get(call[0] + "/schedule/week").param("date", "2026-09-08"),
                        call[1]).andExpect(status().isOk()));

                assertThat(fieldNames(body)).containsExactlyInAnyOrder("semester", "data");
                assertThat(fieldNames(body.get("semester")))
                        .containsExactlyInAnyOrder("id", "code", "name", "startDate", "endDate", "current");
                JsonNode week = body.get("data");
                assertThat(week.get("weekStart").asString()).isEqualTo("2026-09-07");
                assertThat(week.get("days")).hasSize(7);
                List<JsonNode> items = new ArrayList<>();
                week.get("days").forEach(day -> day.get("items").forEach(items::add));
                assertThat(items).isNotEmpty().allSatisfy(item -> {
                    assertThat(item.get("subjectCode").asString()).isEqualTo("TOAN10");
                    assertScheduleItem(item);
                });
                assertThat(items).anySatisfy(item -> {
                    assertThat(item.get("date").asString()).isEqualTo("2026-09-08");
                    assertThat(item.get("startTime").asString()).startsWith("07:30");
                    assertThat(item.get("endTime").asString()).startsWith("09:45");
                });
            }
        }

        @Test
        @DisplayName("Danh sách buổi học và lịch thi trả dạng phân trang trong học kỳ")
        void sessionsAndExams() throws Exception {
            JsonNode sessions = api.json(api.perform(get("/api/v1/student/schedule/sessions"), TestAccounts.STUDENT)
                    .andExpect(status().isOk())).get("data");
            assertPage(sessions);
            assertThat(sessions.get("totalElements").asLong()).isPositive();
            assertScheduleItem(sessions.get("items").get(0));

            JsonNode exams = api.json(api.perform(get(CHILD + "/exams"), TestAccounts.PARENT)
                    .andExpect(status().isOk())).get("data");
            assertPage(exams);
            exams.get("items").forEach(exam -> assertThat(fieldNames(exam)).containsExactlyInAnyOrder("id",
                    "sectionCode", "subjectCode", "subjectName", "examType", "date", "startTime", "endTime",
                    "roomCode", "note"));
        }

        @Test
        @DisplayName("Ngày sai định dạng: 400 VALIDATION_FAILED")
        void invalidDate() throws Exception {
            JsonNode error = api.json(api.perform(get("/api/v1/student/schedule/week").param("date", "08/09/2026x"),
                    TestAccounts.STUDENT).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(error.get("fieldErrors").get(0).get("field").asString()).isEqualTo("date");
            assertThat(error.get("fieldErrors").get(0).get("message").asString()).startsWith("Ngày không hợp lệ");
        }
    }

    @Nested
    @DisplayName("Điểm, chuyên cần")
    class GradesAndAttendance {

        @Test
        @DisplayName("Điểm chưa công bố không lộ qua API; công bố xong SV và PH thấy")
        void gradesHiddenUntilPublished() throws Exception {
            mockMvc.perform(post("/teacher/sections/1/grades").with(csrf()).with(as(TestAccounts.TEACHER))
                            .param("scores[1_1]", "8").param("scores[1_2]", "7").param("scores[1_3]", "9"))
                    .andExpect(redirectedUrl("/teacher/sections/1/grades"));

            assertThat(toan10Scores("/api/v1/student/grades", TestAccounts.STUDENT)).containsOnlyNulls();
            assertThat(toan10Scores(CHILD + "/grades", TestAccounts.PARENT)).containsOnlyNulls();

            mockMvc.perform(post("/admin/sections/1/grades/publish").with(csrf()).with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(redirectedUrl("/admin/sections/1/grades"));

            assertThat(toan10Scores("/api/v1/student/grades", TestAccounts.STUDENT)).containsExactly(8.0, 7.0, 9.0);
            assertThat(toan10Scores(CHILD + "/grades", TestAccounts.PARENT)).containsExactly(8.0, 7.0, 9.0);
        }

        @Test
        @DisplayName("Chuyên cần theo môn kèm ngưỡng cảnh báo; lịch sử từng buổi phân trang")
        void attendance() throws Exception {
            JsonNode overview = api.json(api.perform(get("/api/v1/student/attendance"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(overview)).containsExactlyInAnyOrder("semester", "thresholdPercent", "sections");
            assertThat(overview.get("thresholdPercent").asInt()).isEqualTo(20);
            JsonNode toan10 = find(overview.get("sections"), "sectionId", "1");
            assertThat(fieldNames(toan10)).containsExactlyInAnyOrder("sectionId", "sectionCode", "subjectCode",
                    "subjectName", "summary");
            assertThat(fieldNames(toan10.get("summary"))).containsExactlyInAnyOrder("present", "absentExcused",
                    "absentUnexcused", "totalSessions", "attendanceRate", "absentRate", "warning");

            JsonNode history = api.json(api.perform(get(CHILD + "/attendance/1"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(history)).containsExactlyInAnyOrder("summary", "thresholdPercent", "sessions");
            assertPage(history.get("sessions"));
            history.get("sessions").get("items").forEach(item -> assertThat(fieldNames(item))
                    .containsExactlyInAnyOrder("sessionNo", "date", "startTime", "endTime", "roomCode",
                            "sessionStatus", "status", "note"));
        }
    }

    @Nested
    @DisplayName("Đơn nghỉ")
    class Leave {

        @Test
        @DisplayName("SV gửi đơn nghỉ kèm tệp: 201, chi tiết đơn; tải lại tệp được; người ngoài tải tệp 403")
        void studentSendsLeave() throws Exception {
            MockMultipartHttpServletRequestBuilder request = leave("/api/v1/student/requests/leave", AppTime.today(),
                    AppTime.today().plusDays(1));
            request.file(new MockMultipartFile("files", "giay-kham.pdf", "application/pdf", PDF));

            JsonNode detail = api.json(api.perform(request, TestAccounts.STUDENT).andExpect(status().isCreated()));

            assertThat(fieldNames(detail)).containsExactlyInAnyOrder("id", "type", "title", "reason", "studentCode",
                    "studentName", "requesterName", "sentByParent", "sectionLabel", "fromDate", "toDate", "status",
                    "createdAt", "updatedAt", "reviewerName", "files", "reviews", "canCancel", "canReview");
            assertThat(detail.get("type").asString()).isEqualTo("LEAVE");
            assertThat(detail.get("status").asString()).isEqualTo("PENDING");
            assertThat(detail.get("canCancel").asBoolean()).isTrue();
            assertThat(detail.get("studentCode").asString()).isEqualTo("HS260001");
            JsonNode file = detail.get("files").get(0);
            assertThat(file.get("fileName").asString()).isEqualTo("giay-kham.pdf");

            String url = "/api/v1/requests/" + detail.get("id").asLong() + "/files/" + file.get("id").asLong();
            api.perform(get(url), TestAccounts.STUDENT)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("giay-kham.pdf")))
                    .andExpect(content().bytes(PDF));
            api.perform(get(url), TestAccounts.PARENT).andExpect(status().isOk());
            api.perform(get(url), OUTSIDER).andExpect(status().isForbidden());

            JsonNode list = api.json(api.perform(get("/api/v1/student/requests"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));
            assertPage(list);
            JsonNode item = find(list.get("items"), "id", detail.get("id").asString());
            assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "type", "title", "studentCode",
                    "studentName", "requesterName", "sectionCode", "fromDate", "toDate", "status", "createdAt");
        }

        @Test
        @DisplayName("Thiếu ngày, lý do: 400 kèm lỗi từng ô; ngày sai: thông báo tiếng Việt; quá 14 ngày: lỗi nghiệp vụ")
        void validation() throws Exception {
            long before = requestRepository.count();

            JsonNode missing = api.json(api.perform(multipart("/api/v1/student/requests/leave"), TestAccounts.STUDENT)
                    .andExpect(status().isBadRequest()));
            assertThat(missing.get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(fields(missing)).contains("fromDate", "toDate", "reason");

            JsonNode badDate = api.json(api.perform(multipart("/api/v1/student/requests/leave")
                    .param("fromDate", "31/02/2026").param("toDate", "2026-10-20").param("reason", "Ốm"),
                    TestAccounts.STUDENT).andExpect(status().isBadRequest()));
            assertThat(badDate.get("fieldErrors").get(0).get("field").asString()).isEqualTo("fromDate");
            assertThat(badDate.get("fieldErrors").get(0).get("message").asString()).startsWith("Ngày không hợp lệ");

            JsonNode tooEarly = api.json(api.perform(leave("/api/v1/student/requests/leave",
                    AppTime.today().minusDays(15), AppTime.today()), TestAccounts.STUDENT)
                    .andExpect(status().isBadRequest()));
            assertThat(tooEarly.get("code").asString()).isEqualTo("BUSINESS_ERROR");
            assertThat(tooEarly.get("message").asString()).contains("không được sớm hơn 14 ngày");
            assertThat(requestRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("Hủy đơn chờ duyệt: CANCELLED; hủy lần nữa: 400 BUSINESS_ERROR")
        void cancel() throws Exception {
            long id = createLeave("/api/v1/student/requests/leave", TestAccounts.STUDENT);

            JsonNode cancelled = api.json(api.perform(post("/api/v1/student/requests/" + id + "/cancel"),
                    TestAccounts.STUDENT).andExpect(status().isOk()));
            assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
            assertThat(cancelled.get("canCancel").asBoolean()).isFalse();

            JsonNode again = api.json(api.perform(post("/api/v1/student/requests/" + id + "/cancel"),
                    TestAccounts.STUDENT).andExpect(status().isBadRequest()));
            assertThat(again.get("code").asString()).isEqualTo("BUSINESS_ERROR");
        }

        @Test
        @DisplayName("PH gửi đơn thay con: người gửi là PH; SV thấy đơn; PH xem qua đường dẫn con khác bị 403")
        void parentSendsForChild() throws Exception {
            JsonNode options = api.json(api.perform(get(CHILD + "/requests/leave-options"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(options)).containsExactlyInAnyOrder("studentId", "studentCode", "studentName",
                    "sections");

            long id = createLeave(CHILD + "/requests/leave", TestAccounts.PARENT);

            JsonNode detail = api.json(api.perform(get(CHILD + "/requests/" + id), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(detail.get("sentByParent").asBoolean()).isTrue();
            api.perform(get("/api/v1/student/requests/" + id), TestAccounts.STUDENT).andExpect(status().isOk());
            api.perform(get(OTHER_CHILD + "/requests/" + id), TestAccounts.PARENT).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SV mở đơn không tồn tại hoặc không phải của mình: lỗi 4xx, không lộ dữ liệu")
        void studentCannotOpenOthersRequest() throws Exception {
            MvcResult result = api.perform(get("/api/v1/student/requests/999999"), TestAccounts.STUDENT).andReturn();

            assertThat(result.getResponse().getStatus()).isIn(403, 404);
            assertThat(api.json(result).get("code").asString()).isIn("FORBIDDEN", "NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Thông báo, hồ sơ, học kỳ")
    class Notifications {

        @Test
        @DisplayName("Thông báo mới: chưa đọc; mở chi tiết thì số chưa đọc giảm; tải tệp được; người ngoài 403")
        void readFlow() throws Exception {
            long id = sendToSection1();
            long fileId = notificationFileRepository.findByIdNotificationIdOrderByIdFileIdAsc(id).getFirst()
                    .getFile().getId();
            long unread = unreadCount(TestAccounts.STUDENT);

            JsonNode inbox = api.json(api.perform(get("/api/v1/notifications"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));
            assertPage(inbox);
            JsonNode item = find(inbox.get("items"), "id", String.valueOf(id));
            assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "title", "senderName", "priority",
                    "createdAt", "read");
            assertThat(item.get("read").asBoolean()).isFalse();

            JsonNode detail = api.json(api.perform(get("/api/v1/notifications/" + id), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(detail)).containsExactlyInAnyOrder("id", "title", "content", "priority",
                    "targetLabel", "senderName", "createdAt", "files");
            assertThat(detail.get("content").asString()).contains("Mang theo thẻ sinh viên");
            assertThat(unreadCount(TestAccounts.STUDENT)).isEqualTo(unread - 1);
            assertThat(recipientRepository.countUnread(TestAccounts.STUDENT_ID)).isEqualTo(unread - 1);

            String url = "/api/v1/notifications/" + id + "/files/" + fileId;
            api.perform(get(url), TestAccounts.PARENT)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                    .andExpect(content().bytes(PDF));
            api.perform(get(url), OUTSIDER).andExpect(status().isForbidden());
            api.perform(get("/api/v1/notifications/" + id), OUTSIDER).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Đánh dấu tất cả đã đọc: số chưa đọc về 0")
        void readAll() throws Exception {
            sendToSection1();

            JsonNode result = api.json(api.perform(post("/api/v1/notifications/read-all"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));

            assertThat(result.get("count").asLong()).isZero();
            assertThat(unreadCount(TestAccounts.PARENT)).isZero();
        }

        @Test
        @DisplayName("Hồ sơ của mình không lộ mật khẩu; chưa có ảnh đại diện thì /me/avatar 404 JSON")
        void profile() throws Exception {
            JsonNode profile = api.json(api.perform(get("/api/v1/me/profile"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));

            assertThat(fieldNames(profile)).containsExactlyInAnyOrder("phone", "fullName", "email", "gender",
                    "dateOfBirth", "address", "hasAvatar", "lastLoginAt", "roles");
            assertThat(profile.get("phone").asString()).isEqualTo(TestAccounts.STUDENT);
            assertThat(profile.get("roles").get(0).get("roleCode").asString()).isEqualTo("STUDENT");
            assertThat(profile.toString()).doesNotContain("$2a$");

            if (!profile.get("hasAvatar").asBoolean()) {
                JsonNode error = api.json(api.perform(get("/api/v1/me/avatar"), TestAccounts.STUDENT)
                        .andExpect(status().isNotFound()));
                assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
            }
        }

        @Test
        @DisplayName("Danh sách học kỳ cho ô chọn học kỳ")
        void semesters() throws Exception {
            JsonNode semesters = api.json(api.perform(get("/api/v1/semesters"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));

            assertThat(semesters).isNotEmpty();
            assertThat(fieldNames(semesters.get(0))).contains("id", "code", "name");
        }

        @Test
        @DisplayName("Đánh dấu đã đọc qua API thì trang web cũng thấy đã đọc (cùng database)")
        void sharedWithWeb() throws Exception {
            long id = sendToSection1();
            api.perform(get("/api/v1/notifications/" + id), TestAccounts.STUDENT).andExpect(status().isOk());

            JsonNode inbox = api.json(api.perform(get("/api/v1/notifications"), TestAccounts.STUDENT));
            assertThat(find(inbox.get("items"), "id", String.valueOf(id)).get("read").asBoolean()).isTrue();
            mockMvc.perform(get("/notifications").with(as(TestAccounts.STUDENT))).andExpect(status().isOk());
        }
    }

    private static void assertScheduleItem(JsonNode item) {
        assertThat(fieldNames(item)).containsExactlyInAnyOrder("date", "startTime", "endTime", "subjectCode",
                "subjectName", "sectionCode", "roomCode", "teacherName", "status", "note");
    }

    private static void assertPage(JsonNode page) {
        assertThat(fieldNames(page)).containsExactlyInAnyOrder("items", "page", "size", "totalElements",
                "totalPages");
        assertThat(page.get("items").isArray()).isTrue();
    }

    /** Điểm TOAN10 theo đầu điểm (null nếu chưa công bố). */
    private List<Double> toan10Scores(String url, String phone) throws Exception {
        JsonNode rows = api.json(api.perform(get(url), phone).andExpect(status().isOk())).get("data");
        JsonNode toan10 = find(rows, "subjectCode", "TOAN10");
        assertThat(fieldNames(toan10)).containsExactlyInAnyOrder("sectionCode", "subjectCode", "subjectName",
                "cells", "average");
        List<Double> scores = new ArrayList<>();
        toan10.get("cells").forEach(cell -> {
            assertThat(fieldNames(cell)).containsExactlyInAnyOrder("componentName", "weight", "maxScore", "score");
            scores.add(cell.get("score").isNull() ? null : cell.get("score").asDouble());
        });
        return scores;
    }

    private long createLeave(String url, String phone) throws Exception {
        return api.json(api.perform(leave(url, AppTime.today(), AppTime.today()), phone)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static MockMultipartHttpServletRequestBuilder leave(String url, LocalDate from, LocalDate to) {
        return (MockMultipartHttpServletRequestBuilder) multipart(url)
                .param("fromDate", from.toString()).param("toDate", to.toString()).param("sectionId", "")
                .param("title", "").param("reason", "Em bị sốt, có giấy khám bệnh");
    }

    /** GV 0900000003 gửi thông báo kèm PDF tới lớp TOAN10 (SV và PH nhận) qua form web; trả id. */
    private long sendToSection1() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/notifications/new")
                        .file(new MockMultipartFile("files", "de-cuong.pdf", "application/pdf", PDF))
                        .with(csrf()).with(as(TestAccounts.TEACHER))
                        .param("title", "Lịch kiểm tra giữa kỳ")
                        .param("content", "Ôn tập chương 1–3.\nMang theo thẻ sinh viên.")
                        .param("priority", "IMPORTANT").param("targetType", "CLASS_SECTION").param("sectionId", "1"))
                .andExpect(redirectedUrlPattern("/notifications/*")).andReturn();
        String url = result.getResponse().getRedirectedUrl();
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    private long unreadCount(String phone) throws Exception {
        return api.json(api.perform(get("/api/v1/notifications/unread-count"), phone)
                .andExpect(status().isOk())).get("count").asLong();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.get(field).asString())) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }

    private static List<String> fields(JsonNode error) {
        List<String> names = new ArrayList<>();
        error.get("fieldErrors").forEach(node -> names.add(node.get("field").asString()));
        return names;
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
