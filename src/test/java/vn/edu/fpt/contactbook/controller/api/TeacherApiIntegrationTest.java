package vn.edu.fpt.contactbook.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.ApiClient;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.ApiClient.fieldNames;

/**
 * API của giảng viên (giai đoạn 9d). Dữ liệu mẫu: GV 0900000003 dạy lớp 1 (PRJ301-SE1901-FA26, buổi 1, sinh viên
 * students.id 1 = SE190001); lớp 27 (buổi 41) của giảng viên khác; 0922334455 là giảng viên không phụ trách lớp 1.
 */
@IntegrationTest
class TeacherApiIntegrationTest {

    static final String API = "/api/v1/teacher";
    static final String SHEET = API + "/sections/1/sessions/1/attendance";
    static final String OTHER_TEACHER = "0922334455";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired ClassSessionRepository classSessionRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired UserRepository userRepository;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
        // Buổi 1 trong dữ liệu mẫu là 08/09/2026, đã quá hạn 24 giờ: đưa về hôm nay (test rollback).
        ClassSession session = classSessionRepository.findById(1L).orElseThrow();
        session.setSessionDate(AppTime.today());
        classSessionRepository.saveAndFlush(session);
    }

    @Nested
    @DisplayName("Phân quyền, lịch dạy, lớp")
    class Access {

        @Test
        @DisplayName("TEACHER và TBM gọi được; STUDENT, PARENT, ADMIN 403; không token 401")
        void roles() throws Exception {
            for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.TEACHER_HEAD)) {
                api.perform(get(API + "/sections"), phone).andExpect(status().isOk());
            }
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.PARENT, TestAccounts.ADMIN)) {
                JsonNode error = api.json(api.perform(get(API + "/sections"), phone).andExpect(status().isForbidden()));
                assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
            }
            mockMvc.perform(get(API + "/schedule/week")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Lịch dạy tuần này có PRJ301 (buổi 1 đưa về hôm nay); lịch thi và danh sách buổi phân trang")
        void schedule() throws Exception {
            JsonNode week = api.json(api.perform(get(API + "/schedule/week").param("date", AppTime.today().toString()),
                    TestAccounts.TEACHER).andExpect(status().isOk())).get("data");
            List<String> subjects = new ArrayList<>();
            week.get("days").forEach(day -> day.get("items").forEach(item -> subjects.add(
                    item.get("subjectCode").asString())));
            assertThat(subjects).contains("PRJ301");

            JsonNode sessions = api.json(api.perform(get(API + "/schedule/sessions"), TestAccounts.TEACHER)
                    .andExpect(status().isOk())).get("data");
            assertThat(sessions.get("totalElements").asLong()).isPositive();
            api.perform(get(API + "/exams"), TestAccounts.TEACHER).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Danh sách lớp chỉ gồm lớp mình dạy; thống kê lớp có buổi và chuyên cần từng sinh viên")
        void sections() throws Exception {
            JsonNode sections = api.json(api.perform(get(API + "/sections"), TestAccounts.TEACHER)
                    .andExpect(status().isOk())).get("data");
            List<String> codes = new ArrayList<>();
            sections.get("items").forEach(item -> {
                assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "code", "subjectCode", "subjectName",
                        "semesterCode", "status");
                codes.add(item.get("code").asString());
            });
            assertThat(codes).contains("PRJ301-SE1901-FA26").doesNotContain("PRN211_FA26_01");

            JsonNode stats = api.json(api.perform(get(API + "/sections/1/attendance"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(stats)).containsExactlyInAnyOrder("section", "sessions", "students",
                    "totalSessions", "thresholdPercent");
            assertThat(fieldNames(stats.get("sessions").get("items").get(0))).containsExactlyInAnyOrder("id",
                    "sessionNo", "date", "startTime", "endTime", "roomCode", "status", "note", "hasAttendance");
            JsonNode student = stats.get("students").get("items").get(0);
            assertThat(fieldNames(student)).containsExactlyInAnyOrder("studentId", "studentCode", "fullName",
                    "summary");
            assertThat(student.get("studentCode").asString()).isEqualTo("SE190001");
        }

        @Test
        @DisplayName("Lớp, buổi của giảng viên khác: 403, không ghi gì; buổi không thuộc lớp: 404")
        void otherSection() throws Exception {
            api.perform(get(API + "/sections/27/attendance"), TestAccounts.TEACHER).andExpect(status().isForbidden());
            api.perform(get(API + "/sections/27/sessions/41/attendance"), TestAccounts.TEACHER)
                    .andExpect(status().isForbidden());
            api.perform(save(API + "/sections/27/sessions/41/attendance",
                    Map.of("2", "ABSENT_UNEXCUSED", "3", "PRESENT"), "x"), TestAccounts.TEACHER)
                    .andExpect(status().isForbidden());
            assertThat(attendanceRepository.findBySessionId(41L)).extracting(Attendance::getStatus)
                    .containsOnly(AttendanceStatus.PRESENT);

            JsonNode error = api.json(api.perform(get(API + "/sections/1/sessions/41/attendance"),
                    TestAccounts.TEACHER).andExpect(status().isNotFound()));
            assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Điểm danh một buổi")
    class Marking {

        @Test
        @DisplayName("Bảng điểm danh buổi 1: thông tin buổi và từng sinh viên, chưa điểm danh")
        void sheet() throws Exception {
            JsonNode sheet = api.json(api.perform(get(SHEET), TestAccounts.TEACHER).andExpect(status().isOk()));

            assertThat(fieldNames(sheet)).containsExactlyInAnyOrder("sectionId", "sectionCode", "subjectCode",
                    "subjectName", "sessionId", "sessionNo", "date", "startTime", "endTime", "roomCode", "marked",
                    "editableUntil", "locked", "reasonRequired", "rows");
            assertThat(sheet.get("marked").asBoolean()).isFalse();
            assertThat(sheet.get("locked").asBoolean()).isFalse();
            assertThat(sheet.get("editableUntil").asString())
                    .startsWith(AppTime.today().plusDays(1).toString());
            JsonNode row = sheet.get("rows").get(0);
            assertThat(fieldNames(row)).containsExactlyInAnyOrder("studentId", "studentCode", "fullName", "status",
                    "note", "approvedLeave");
            assertThat(row.get("studentId").asLong()).isEqualTo(1);
            assertThat(row.get("status").isNull()).isTrue();
        }

        @Test
        @DisplayName("Lần đầu không cần lý do; sửa lại thiếu lý do 400; có lý do thì lưu, audit, SV thấy ngay")
        void markThenAdjust() throws Exception {
            JsonNode first = api.json(api.perform(save(SHEET, Map.of("1", "PRESENT"), null), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(first)).containsExactlyInAnyOrder("firstTime", "changed", "sheet");
            assertThat(first.get("firstTime").asBoolean()).isTrue();
            assertThat(first.get("changed").asInt()).isEqualTo(1);
            assertThat(first.get("sheet").get("marked").asBoolean()).isTrue();
            assertThat(lastAudit(AuditActions.MARK_ATTENDANCE).getUser().getId()).isEqualTo(TestAccounts.TEACHER_ID);

            JsonNode noReason = api.json(api.perform(save(SHEET, Map.of("1", "ABSENT_EXCUSED"), null),
                    TestAccounts.TEACHER).andExpect(status().isBadRequest()));
            assertThat(noReason.get("code").asString()).isEqualTo("BUSINESS_ERROR");
            assertThat(noReason.get("message").asString()).isEqualTo("Vui lòng nhập lý do điều chỉnh điểm danh");
            assertThat(attendanceRepository.findBySessionId(1L).getFirst().getStatus())
                    .isEqualTo(AttendanceStatus.PRESENT);

            JsonNode adjusted = api.json(api.perform(save(SHEET, Map.of("1", "ABSENT_EXCUSED"), "Có giấy khám bệnh"),
                    TestAccounts.TEACHER).andExpect(status().isOk()));
            assertThat(adjusted.get("firstTime").asBoolean()).isFalse();
            assertThat(adjusted.get("changed").asInt()).isEqualTo(1);
            assertThat(adjusted.get("sheet").get("rows").get(0).get("note").isNull()).isTrue();
            assertThat(lastAudit(AuditActions.ADJUST_ATTENDANCE).getDetail())
                    .contains("status=PRESENT -> ABSENT_EXCUSED");

            JsonNode history = api.json(api.perform(get("/api/v1/student/attendance/1"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));
            assertThat(history.get("summary").get("summary").get("absentExcused").asLong()).isPositive();
        }

        @Test
        @DisplayName("Trạng thái không hợp lệ hoặc thiếu sinh viên: 400, không ghi")
        void invalidInput() throws Exception {
            JsonNode badStatus = api.json(api.perform(save(SHEET, Map.of("1", "HACKED"), null), TestAccounts.TEACHER)
                    .andExpect(status().isBadRequest()));
            assertThat(badStatus.get("code").asString()).isEqualTo("VALIDATION_FAILED");

            JsonNode missing = api.json(api.perform(save(SHEET, Map.of(), null), TestAccounts.TEACHER)
                    .andExpect(status().isBadRequest()));
            assertThat(missing.get("code").asString()).isEqualTo("BUSINESS_ERROR");

            JsonNode longReason = api.json(api.perform(save(SHEET, Map.of("1", "PRESENT"), "x".repeat(256)),
                    TestAccounts.TEACHER).andExpect(status().isBadRequest()));
            assertThat(longReason.get("fieldErrors").get(0).get("field").asString()).isEqualTo("reason");
            assertThat(attendanceRepository.findBySessionId(1L)).isEmpty();
        }

        @Test
        @DisplayName("Buổi chưa diễn ra: 400 BUSINESS_ERROR, không mở được, không ghi")
        void futureSession() throws Exception {
            ClassSession session = new ClassSession();
            session.setClassSection(classSectionRepository.findById(1L).orElseThrow());
            session.setSessionNo((short) 99);
            session.setSessionDate(AppTime.today().plusDays(7));
            session.setStartTime(LocalTime.of(13, 0));
            session.setEndTime(LocalTime.of(15, 15));
            Long future = classSessionRepository.saveAndFlush(session).getId();
            String url = API + "/sections/1/sessions/" + future + "/attendance";

            JsonNode error = api.json(api.perform(get(url), TestAccounts.TEACHER).andExpect(status().isBadRequest()));
            assertThat(error.get("message").asString()).contains("Buổi học chưa diễn ra");
            api.perform(save(url, Map.of("1", "PRESENT"), null), TestAccounts.TEACHER)
                    .andExpect(status().isBadRequest());
            assertThat(attendanceRepository.findBySessionId(future)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ghi chú và hạn 24 giờ qua API")
    class NotesAndWindow {

        @Test
        @DisplayName("Lưu kèm notes: ghi chú lưu theo học sinh; lần sau không gửi notes thì ghi chú giữ nguyên")
        void notesAreKept() throws Exception {
            api.perform(saveWithNotes(SHEET, Map.of("1", "PRESENT"), Map.of("1", "Đi muộn 10 phút"), null),
                    TestAccounts.TEACHER).andExpect(status().isOk());

            JsonNode adjusted = api.json(api.perform(save(SHEET, Map.of("1", "ABSENT_UNEXCUSED"), "Nhập nhầm"),
                    TestAccounts.TEACHER).andExpect(status().isOk()));

            JsonNode row = adjusted.get("sheet").get("rows").get(0);
            assertThat(row.get("status").asString()).isEqualTo("ABSENT_UNEXCUSED");
            assertThat(row.get("note").asString()).isEqualTo("Đi muộn 10 phút");
        }

        @Test
        @DisplayName("Quá 24 giờ từ lần điểm danh đầu: bảng locked, lưu bị 400 BUSINESS_ERROR")
        void lockedAfterWindow() throws Exception {
            Attendance row = new Attendance();
            row.setSession(classSessionRepository.findById(1L).orElseThrow());
            row.setStudent(studentRepository.findById(1L).orElseThrow());
            row.setStatus(AttendanceStatus.PRESENT);
            row.setMarkedAt(AppTime.now().minusHours(25));
            row.setMarkedBy(userRepository.findById(TestAccounts.TEACHER_ID).orElseThrow());
            attendanceRepository.saveAndFlush(row);

            JsonNode sheet = api.json(api.perform(get(SHEET), TestAccounts.TEACHER).andExpect(status().isOk()));
            assertThat(sheet.get("locked").asBoolean()).isTrue();
            JsonNode error = api.json(api.perform(save(SHEET, Map.of("1", "ABSENT_UNEXCUSED"), "Khiếu nại"),
                    TestAccounts.TEACHER).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asString()).isEqualTo("BUSINESS_ERROR");
            assertThat(error.get("message").asString()).startsWith("Đã hết hạn điểm danh, chỉnh sửa");
        }

        @Test
        @DisplayName("Trạng thái LATE đã bỏ: 400 VALIDATION_FAILED")
        void lateIsRejected() throws Exception {
            JsonNode error = api.json(api.perform(save(SHEET, Map.of("1", "LATE"), null), TestAccounts.TEACHER)
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Nested
    @DisplayName("Duyệt đơn")
    class Review {

        @Test
        @DisplayName("SV gửi đơn lớp 1; GV phụ trách thấy trong PENDING, duyệt được; SV thấy APPROVED; duyệt lại 400")
        void approve() throws Exception {
            long id = sendLeave();

            JsonNode queue = api.json(api.perform(get(API + "/requests"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            List<Long> ids = new ArrayList<>();
            queue.get("items").forEach(item -> ids.add(item.get("id").asLong()));
            assertThat(ids).contains(id);

            JsonNode detail = api.json(api.perform(get(API + "/requests/" + id), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(detail.get("canReview").asBoolean()).isTrue();

            JsonNode result = api.json(api.perform(review(id, "APPROVED", null), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(fieldNames(result)).containsExactlyInAnyOrder("decision", "excusedSessions", "request");
            assertThat(result.get("decision").asString()).isEqualTo("APPROVED");
            assertThat(result.get("excusedSessions").asInt()).isNotNegative();
            assertThat(result.get("request").get("status").asString()).isEqualTo("APPROVED");

            JsonNode seen = api.json(api.perform(get("/api/v1/student/requests/" + id), TestAccounts.STUDENT));
            assertThat(seen.get("status").asString()).isEqualTo("APPROVED");
            api.perform(review(id, "APPROVED", null), TestAccounts.TEACHER).andExpect(status().isBadRequest());

            JsonNode approved = api.json(api.perform(get(API + "/requests").param("status", "APPROVED"),
                    TestAccounts.TEACHER).andExpect(status().isOk()));
            List<Long> approvedIds = new ArrayList<>();
            approved.get("items").forEach(item -> approvedIds.add(item.get("id").asLong()));
            assertThat(approvedIds).contains(id);
        }

        @Test
        @DisplayName("Từ chối thiếu ghi chú, thiếu decision: 400; có ghi chú: REJECTED")
        void reject() throws Exception {
            long id = sendLeave();

            JsonNode noComment = api.json(api.perform(review(id, "REJECTED", null), TestAccounts.TEACHER)
                    .andExpect(status().isBadRequest()));
            assertThat(noComment.get("message").asString()).isEqualTo("Vui lòng nhập ghi chú khi từ chối đơn");
            api.perform(review(id, null, "x"), TestAccounts.TEACHER).andExpect(status().isBadRequest());

            JsonNode rejected = api.json(api.perform(review(id, "REJECTED", "Thiếu minh chứng"), TestAccounts.TEACHER)
                    .andExpect(status().isOk()));
            assertThat(rejected.get("request").get("status").asString()).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("Giảng viên không phụ trách mở hoặc duyệt đơn: 403")
        void otherTeacher() throws Exception {
            long id = sendLeave();

            api.perform(get(API + "/requests/" + id), OTHER_TEACHER).andExpect(status().isForbidden());
            api.perform(review(id, "APPROVED", null), OTHER_TEACHER).andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder save(String url, Map<String, String> statuses, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("statuses", statuses);
        body.put("reason", reason);
        return post(url).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder saveWithNotes(String url, Map<String, String> statuses,
                                                       Map<String, String> notes, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("statuses", statuses);
        body.put("notes", notes);
        body.put("reason", reason);
        return post(url).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder review(long id, String decision, String comment) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        body.put("comment", comment);
        return post(API + "/requests/" + id + "/review").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body));
    }

    /** SV gửi đơn nghỉ hôm nay cho lớp 1 qua API; người duyệt là giảng viên của lớp 1. */
    private long sendLeave() throws Exception {
        return api.json(api.perform(multipart("/api/v1/student/requests/leave")
                        .param("fromDate", AppTime.today().toString()).param("toDate", AppTime.today().toString())
                        .param("sectionId", "1").param("reason", "Em bị sốt, có giấy khám bệnh"), TestAccounts.STUDENT)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private AuditLog lastAudit(String action) {
        return auditLogRepository.findAll().stream().filter(entry -> action.equals(entry.getAction()))
                .max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }
}
