package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.repository.RequestFileRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đơn từ trên FptContactBook_test. Dữ liệu mẫu: HS260001 (students.id 1, users.id 4) học lớp học phần 1 do GV
 * 0900000003 (users.id 3) dạy, lớp hành chính 10A1 cũng do GV này chủ nhiệm; phụ huynh 0900000005 (users.id 5)
 * liên kết HS260001; students.id 2 (HS250101, 0933112233) không liên kết phụ huynh này. GV 0900000002 không dạy lớp 1.
 */
@IntegrationTest
class RequestIntegrationTest {

    static final String OUTSIDER = "0933112233";
    static final byte[] PDF = "%PDF-1.7\ngiay kham benh".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired UserRequestRepository requestRepository;
    @Autowired RequestFileRepository requestFileRepository;
    @Autowired NotificationRecipientRepository recipientRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired ClassSessionRepository classSessionRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("Gửi đơn")
    class Send {

        @Test
        @DisplayName("HS gửi đơn nghỉ kèm minh chứng: PENDING, tiêu đề tự điền, GV lớp nhận thông báo, có audit")
        void studentSendsLeave() throws Exception {
            mockMvc.perform(get("/student/requests/new").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("TOAN10-HK1-2627-01")));
            long teacherUnread = recipientRepository.countUnread(TestAccounts.TEACHER_ID);

            Long id = sendLeave(leave("/student/requests/new", AppTime.today(), AppTime.today().plusDays(1), "1")
                    .file(new MockMultipartFile("files", "giay-kham.pdf", "application/pdf", PDF))
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");

            UserRequest saved = requestRepository.findDetailById(id).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(saved.getRequestType()).isEqualTo(RequestType.LEAVE);
            assertThat(saved.getRequester().getId()).isEqualTo(TestAccounts.STUDENT_ID);
            assertThat(saved.getStudent().getId()).isEqualTo(1L);
            assertThat(saved.getClassSection().getId()).isEqualTo(1L);
            assertThat(saved.getTitle()).startsWith("Đơn xin nghỉ học từ ");
            assertThat(requestFileRepository.findByIdRequestIdOrderByIdFileIdAsc(id)).hasSize(1);
            assertThat(recipientRepository.countUnread(TestAccounts.TEACHER_ID)).isEqualTo(teacherUnread + 1);
            assertThat(lastAudit(AuditActions.CREATE_REQUEST).getEntityId()).isEqualTo(id);

            mockMvc.perform(get("/student/requests").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(saved.getTitle())));
            mockMvc.perform(get("/student/requests/" + id).with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("giay-kham.pdf"), containsString("Hủy đơn"))));
        }

        @Test
        @DisplayName("Ngày sai ngưỡng: lỗi cạnh ô, không lưu")
        void invalidDates() throws Exception {
            long before = requestRepository.count();

            mockMvc.perform(leave("/student/requests/new", AppTime.today().minusDays(15), AppTime.today(), "")
                            .with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("không được sớm hơn 14 ngày")));
            assertThat(requestRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("PH gửi thay con đã liên kết: người gửi là PH; HS thấy đơn trong danh sách của mình")
        void parentSendsForChild() throws Exception {
            Long id = sendLeave(leave("/parent/children/1/requests/new", AppTime.today(), AppTime.today(), "")
                    .with(as(TestAccounts.PARENT)), "/parent/children/1/requests/");

            UserRequest saved = requestRepository.findDetailById(id).orElseThrow();
            assertThat(saved.getRequester().getId()).isEqualTo(TestAccounts.PARENT_ID);
            assertThat(saved.getStudent().getId()).isEqualTo(1L);
            mockMvc.perform(get("/student/requests/" + id).with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Phụ huynh gửi thay"),
                            not(containsString("Hủy đơn")))));
            mockMvc.perform(get("/parent/requests").with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/parent/children/1/requests"));
        }

        @Test
        @DisplayName("PH gửi hoặc xem đơn của HS chưa liên kết → 403")
        void parentUnlinkedChildForbidden() throws Exception {
            long before = requestRepository.count();

            mockMvc.perform(leave("/parent/children/2/requests/new", AppTime.today(), AppTime.today(), "")
                            .with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/2/requests").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            assertThat(requestRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("HS gửi đơn khác: ADMIN thấy trong /admin/requests và duyệt được")
        void otherRequestGoesToAdmin() throws Exception {
            MvcResult result = mockMvc.perform(multipart("/student/requests/new-other").with(csrf())
                            .param("requestType", "TRANSCRIPT").param("title", "Xin bảng điểm học kỳ HK1-2627")
                            .param("reason", "Nộp hồ sơ học bổng").with(as(TestAccounts.STUDENT)))
                    .andExpect(redirectedUrlPattern("/student/requests/*"))
                    .andReturn();
            Long id = idFrom(result, "/student/requests/");

            mockMvc.perform(get("/admin/requests").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Xin bảng điểm học kỳ HK1-2627")));
            mockMvc.perform(get("/teacher/requests/" + id).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/requests/" + id + "/review").with(csrf()).param("decision", "APPROVED")
                            .with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/requests/" + id));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.APPROVED);
        }

        @Test
        @DisplayName("Duyệt đơn giữ ghi chú của giáo viên, nối thêm tham chiếu đơn; không bị chặn bởi hạn 24 giờ")
        void approveKeepsTeacherNote() throws Exception {
            LocalDate today = AppTime.today();
            // Điểm danh từ 5 ngày trước: giáo viên đã hết hạn sửa, nhưng duyệt đơn vẫn chuyển được.
            Attendance absent = new Attendance();
            absent.setSession(session(1L, today.minusDays(5)));
            absent.setStudent(studentRepository.findById(1L).orElseThrow());
            absent.setStatus(AttendanceStatus.ABSENT_UNEXCUSED);
            absent.setNote("Không liên lạc được phụ huynh");
            absent.setMarkedAt(AppTime.now().minusDays(5));
            absent.setMarkedBy(userRepository.findById(TestAccounts.TEACHER_ID).orElseThrow());
            attendanceRepository.saveAndFlush(absent);

            Long id = sendLeave(leave("/student/requests/new", today.minusDays(6), today.minusDays(1), "1")
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");
            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "APPROVED")
                            .param("comment", "Đồng ý").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/teacher/requests/" + id));

            Attendance reloaded = reload(absent);
            assertThat(reloaded.getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
            assertThat(reloaded.getNote()).isEqualTo("Không liên lạc được phụ huynh · Vắng có phép theo đơn #" + id);
        }
    }

    @Nested
    @DisplayName("Duyệt đơn nghỉ")
    class Approve {

        @Test
        @DisplayName("GV duyệt: chỉ vắng không phép trong khoảng ngày, đúng lớp thành vắng có phép; có audit; "
                + "HS nhận thông báo; bảng điểm danh hiện nhãn; đơn đã duyệt không hủy được")
        void approveExcusesAbsences() throws Exception {
            LocalDate today = AppTime.today();
            Attendance inRange = attendance(session(1L, today.minusDays(5)), AttendanceStatus.ABSENT_UNEXCUSED);
            Attendance present = attendance(session(1L, today.minusDays(3)), AttendanceStatus.PRESENT);
            Attendance outOfRange = attendance(session(1L, today.minusDays(20)), AttendanceStatus.ABSENT_UNEXCUSED);
            Attendance otherSection = attendance(session(27L, today.minusDays(4)), AttendanceStatus.ABSENT_UNEXCUSED);
            Long markedSession = inRange.getSession().getId();

            Long id = sendLeave(leave("/student/requests/new", today.minusDays(6), today.minusDays(1), "1")
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");
            mockMvc.perform(get("/teacher/requests").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/teacher/requests/" + id)));
            mockMvc.perform(get("/teacher/requests/" + id).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"decision\"")));

            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "APPROVED")
                            .param("comment", "Đồng ý").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/teacher/requests/" + id))
                    .andExpect(flash().attribute("successMessage", containsString("1 buổi")));

            assertThat(reload(inRange).getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
            assertThat(reload(inRange).getNote()).isEqualTo("Vắng có phép theo đơn #" + id);
            assertThat(reload(inRange).getUpdatedAt()).isNotNull();
            assertThat(reload(present).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(reload(outOfRange).getStatus()).isEqualTo(AttendanceStatus.ABSENT_UNEXCUSED);
            assertThat(reload(otherSection).getStatus()).isEqualTo(AttendanceStatus.ABSENT_UNEXCUSED);
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.APPROVED);
            AuditLog excuse = lastAudit(AuditActions.EXCUSE_ATTENDANCE);
            assertThat(excuse.getEntityId()).isEqualTo(inRange.getId());
            assertThat(excuse.getDetail()).contains("ABSENT_UNEXCUSED -> ABSENT_EXCUSED", "request=" + id);
            assertThat(lastAudit(AuditActions.APPROVE_REQUEST).getEntityId()).isEqualTo(id);
            assertThat(recipientRepository.findInbox(TestAccounts.STUDENT_ID, PageRequest.of(0, 5)).getContent())
                    .anySatisfy(nr -> assertThat(nr.getNotification().getTitle()).startsWith("Đơn đã được duyệt"));

            mockMvc.perform(get("/teacher/sections/1/sessions/" + markedSession + "/attendance")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Có đơn nghỉ được duyệt")));

            mockMvc.perform(post("/student/requests/" + id + "/cancel").with(csrf()).with(as(TestAccounts.STUDENT)))
                    .andExpect(redirectedUrl("/student/requests/" + id))
                    .andExpect(flash().attribute("errorMessage", "Đơn không còn ở trạng thái chờ duyệt"));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.APPROVED);
        }

        @Test
        @DisplayName("Từ chối thiếu ghi chú: báo lỗi, đơn vẫn chờ duyệt; có ghi chú: REJECTED")
        void rejectNeedsComment() throws Exception {
            Long id = sendLeave(leave("/student/requests/new", AppTime.today(), AppTime.today(), "1")
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");

            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "REJECTED")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(flash().attribute("errorMessage", "Vui lòng nhập ghi chú khi từ chối đơn"));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.PENDING);

            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "REJECTED")
                            .param("comment", "Thiếu minh chứng").with(as(TestAccounts.TEACHER)))
                    .andExpect(flash().attribute("successMessage", "Đã từ chối đơn"));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.REJECTED);
        }

        @Test
        @DisplayName("Hủy đơn PENDING được; sau đó GV duyệt đơn đã hủy bị từ chối")
        void cancelThenApproveFails() throws Exception {
            Long id = sendLeave(leave("/student/requests/new", AppTime.today(), AppTime.today(), "1")
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");

            mockMvc.perform(post("/student/requests/" + id + "/cancel").with(csrf()).with(as(TestAccounts.STUDENT)))
                    .andExpect(flash().attribute("successMessage", "Đã hủy đơn"));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(lastAudit(AuditActions.CANCEL_REQUEST).getEntityId()).isEqualTo(id);

            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "APPROVED")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(flash().attribute("errorMessage", "Đơn không còn ở trạng thái chờ duyệt"));
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("GV không phải người duyệt mở hoặc duyệt đơn → 403")
        void otherTeacherForbidden() throws Exception {
            Long id = sendLeave(leave("/student/requests/new", AppTime.today(), AppTime.today(), "1")
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");

            mockMvc.perform(get("/teacher/requests/" + id).with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/teacher/requests/" + id + "/review").with(csrf()).param("decision", "APPROVED")
                            .with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.PENDING);
        }

        @Test
        @DisplayName("Tệp minh chứng: HS, PH liên kết, GV duyệt, ADMIN tải được; người không liên quan → 403")
        void attachmentAccess() throws Exception {
            Long id = sendLeave(leave("/student/requests/new", AppTime.today(), AppTime.today(), "1")
                    .file(new MockMultipartFile("files", "giay-kham.pdf", "application/pdf", PDF))
                    .with(as(TestAccounts.STUDENT)), "/student/requests/");
            Long fileId = requestFileRepository.findByIdRequestIdOrderByIdFileIdAsc(id).getFirst().getFile().getId();
            String url = "/requests/" + id + "/files/" + fileId;

            mockMvc.perform(get(url).with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(content().bytes(PDF));
            for (String phone : List.of(TestAccounts.PARENT, TestAccounts.TEACHER, TestAccounts.ADMIN)) {
                mockMvc.perform(get(url).with(as(phone))).andExpect(status().isOk());
            }
            mockMvc.perform(get(url).with(as(OUTSIDER))).andExpect(status().isForbidden());
            mockMvc.perform(get(url).with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isForbidden());
            mockMvc.perform(get("/student/requests/" + id).with(as(OUTSIDER))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Sai vai trò → 403: STUDENT vào /admin/requests, /teacher/requests; PARENT vào /student/requests")
        void wrongRole() throws Exception {
            mockMvc.perform(get("/admin/requests").with(as(TestAccounts.STUDENT))).andExpect(status().isForbidden());
            mockMvc.perform(get("/teacher/requests").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/student/requests").with(as(TestAccounts.PARENT))).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/requests").with(as(TestAccounts.TEACHER))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Đúng vai trò → 200 và menu có mục mới")
        void rightRole() throws Exception {
            mockMvc.perform(get("/student/requests").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("/student/requests")));
            mockMvc.perform(get("/parent/children/1/requests").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Đơn của con")));
            mockMvc.perform(get("/teacher/requests").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Duyệt đơn")));
            mockMvc.perform(get("/admin/requests").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Xử lý đơn")));
        }

        @Test
        @DisplayName("Chưa đăng nhập → /login")
        void anonymousRedirected() throws Exception {
            mockMvc.perform(get("/student/requests")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/teacher/requests")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/requests")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/requests/1/files/1")).andExpect(redirectedUrl("/login"));
        }
    }

    private MockMultipartHttpServletRequestBuilder leave(String url, LocalDate from, LocalDate to, String sectionId) {
        return (MockMultipartHttpServletRequestBuilder) multipart(url).with(csrf())
                .param("fromDate", from.toString()).param("toDate", to.toString()).param("sectionId", sectionId)
                .param("title", "").param("reason", "Em bị sốt, có giấy khám bệnh");
    }

    private Long sendLeave(RequestBuilder request, String prefix) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(redirectedUrlPattern(prefix + "*")).andReturn();
        return idFrom(result, prefix);
    }

    private static Long idFrom(MvcResult result, String prefix) {
        return Long.valueOf(result.getResponse().getRedirectedUrl().substring(prefix.length()));
    }

    private ClassSession session(Long sectionId, LocalDate date) {
        ClassSession session = new ClassSession();
        session.setClassSection(classSectionRepository.findById(sectionId).orElseThrow());
        session.setSessionNo((short) (90 + date.getDayOfMonth() % 9));
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(18, 0));
        session.setEndTime(LocalTime.of(20, 15));
        return classSessionRepository.saveAndFlush(session);
    }

    private Attendance attendance(ClassSession session, AttendanceStatus status) {
        Attendance row = new Attendance();
        row.setSession(session);
        row.setStudent(studentRepository.findById(1L).orElseThrow());
        row.setStatus(status);
        row.setNote("");
        row.setMarkedBy(userRepository.findById(TestAccounts.TEACHER_ID).orElseThrow());
        return attendanceRepository.saveAndFlush(row);
    }

    private Attendance reload(Attendance row) {
        return attendanceRepository.findById(row.getId()).orElseThrow();
    }

    private AuditLog lastAudit(String action) {
        return auditLogRepository.findAll().stream().filter(a -> action.equals(a.getAction()))
                .max(java.util.Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
