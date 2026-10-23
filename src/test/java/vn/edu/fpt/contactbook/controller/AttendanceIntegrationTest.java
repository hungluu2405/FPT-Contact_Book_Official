package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Điểm danh trên FptContactBook_test. Dữ liệu mẫu: lớp 1 (TOAN10-HK1-2627-01, GV002 = 0900000003, buổi 1 ngày
 * 08/09/2026 chưa điểm danh, có HS260001 = students.id 1); lớp 27 (TIN11, GV003 = 0922334455, buổi 41–46, buổi
 * 41 đã điểm danh PRESENT cho HS250101 (students.id 2) và HS250102 (students.id 3)). Phụ huynh 0900000005 liên kết
 * HS260001; 0944112233 liên kết HS250101. Trưởng bộ môn 0900000002 thuộc bộ môn SE.
 */
@IntegrationTest
class AttendanceIntegrationTest {

    static final String GV003 = "0922334455";
    static final String HS250101 = "0933112233";
    static final String PARENT_OF_HS250101 = "0944112233";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ClassSessionRepository classSessionRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired UserRepository userRepository;

    /**
     * Buổi 1 của lớp 1 trong dữ liệu mẫu là 08/09/2026, đã quá hạn 24 giờ của giáo viên. Đưa buổi về hôm nay để giáo
     * viên còn hạn điểm danh (test rollback nên không đổi dữ liệu thật).
     */
    @BeforeEach
    void sessionOneToday() {
        ClassSession session = classSessionRepository.findById(1L).orElseThrow();
        session.setSessionDate(AppTime.today());
        classSessionRepository.saveAndFlush(session);
    }

    @Nested
    @DisplayName("Hạn 24 giờ của giáo viên")
    class EditWindow {

        @Test
        @DisplayName("Còn hạn: bảng điểm danh hiện mốc hạn, có ô ghi chú và 3 nút")
        void showsDeadline() throws Exception {
            mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Hạn điểm danh, chỉnh sửa của giáo viên:"),
                            containsString("name=\"notes[1]\""), containsString("Vắng có phép"),
                            not(containsString("value=\"LATE\"")))));
        }

        @Test
        @DisplayName("Quá 24 giờ từ lần điểm danh đầu: giáo viên chỉ xem, lưu bị từ chối; ADMIN vẫn sửa được kèm lý do")
        void lockedAfterWindow() throws Exception {
            Attendance row = new Attendance();
            row.setSession(classSessionRepository.findById(1L).orElseThrow());
            row.setStudent(studentRepository.findById(1L).orElseThrow());
            row.setStatus(AttendanceStatus.PRESENT);
            row.setMarkedAt(AppTime.now().minusHours(25));
            row.setMarkedBy(userRepository.findById(TestAccounts.TEACHER_ID).orElseThrow());
            attendanceRepository.saveAndFlush(row);

            mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Đã hết hạn điểm danh, chỉnh sửa"),
                            not(containsString("class=\"btn-check\"")))));
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "ABSENT_UNEXCUSED").param("reason", "Khiếu nại muộn")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Đã hết hạn điểm danh, chỉnh sửa")));
            assertThat(attendanceRepository.findBySessionId(1L).getFirst().getStatus())
                    .isEqualTo(AttendanceStatus.PRESENT);

            mockMvc.perform(post("/admin/sections/1/sessions/1/attendance").with(csrf())
                            .param("statuses[1]", "ABSENT_EXCUSED").param("reason", "Có minh chứng")
                            .with(as(TestAccounts.ADMIN)))
                    .andExpect(status().is3xxRedirection());
            assertThat(attendanceRepository.findBySessionId(1L).getFirst().getStatus())
                    .isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
        }

        @Test
        @DisplayName("Buổi đã kết thúc quá 24 giờ mà chưa điểm danh: giáo viên bị chặn, không ghi gì")
        void firstMarkAfterWindow() throws Exception {
            ClassSession session = classSessionRepository.findById(1L).orElseThrow();
            session.setSessionDate(AppTime.today().minusDays(2));
            classSessionRepository.saveAndFlush(session);

            mockMvc.perform(mark(1L, 1L, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Đã hết hạn điểm danh, chỉnh sửa")));
            assertThat(attendanceRepository.findBySessionId(1L)).isEmpty();
        }

        @Test
        @DisplayName("Trạng thái LATE (đã bỏ) gửi bằng tay: hiện lại form, không ghi")
        void lateIsRejected() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "LATE").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Trạng thái điểm danh không hợp lệ")));
            assertThat(attendanceRepository.findBySessionId(1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Giáo viên điểm danh")
    class TeacherMarks {

        @Test
        @DisplayName("0900000003 thấy lớp mình dạy và mở được bảng điểm danh buổi 1 (200)")
        void teacherOpensOwnSection() throws Exception {
            mockMvc.perform(get("/teacher/sections").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10-HK1-2627-01"),
                            not(containsString("TIN11-HK1-2627-01")))));
            mockMvc.perform(get("/teacher/sections/1").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("HS260001")));
            mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("HS260001"),
                            containsString("statuses[1]"), containsString("_csrf"))));
        }

        @Test
        @DisplayName("Lưu lần đầu: có mặt kèm ghi chú đi muộn, marked_by là giáo viên, không cần lý do, audit")
        void teacherSavesFirstTime() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "PRESENT").param("notes[1]", " Đi muộn 10 phút ")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/teacher/sections/1"))
                    .andExpect(flash().attribute("successMessage", "Đã lưu điểm danh buổi 1 (1 học sinh)"));

            assertThat(attendanceRepository.findBySessionId(1L))
                    .extracting(a -> a.getStudent().getId(), Attendance::getStatus, a -> a.getMarkedBy().getId(),
                            Attendance::getNote)
                    .containsExactly(tuple(1L, AttendanceStatus.PRESENT, TestAccounts.TEACHER_ID, "Đi muộn 10 phút"));
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.MARK_ATTENDANCE);
            assertThat(audit.getUser().getId()).isEqualTo(TestAccounts.TEACHER_ID);
            assertThat(audit.getDetail()).contains("PRESENT=1", "ABSENT_EXCUSED=0", "ABSENT_UNEXCUSED=0");
        }

        @Test
        @DisplayName("Điều chỉnh sau lần lưu đầu: thiếu lý do thì báo lỗi; có lý do thì audit cũ → mới, lý do không đè ghi chú")
        void teacherAdjustsWithReason() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(mark(1L, 1L, "statuses[1]", "ABSENT_EXCUSED").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vui lòng nhập lý do điều chỉnh điểm danh")));
            assertThat(attendanceRepository.findBySessionId(1L).getFirst().getStatus())
                    .isEqualTo(AttendanceStatus.PRESENT);

            mockMvc.perform(mark(1L, 1L, "statuses[1]", "ABSENT_EXCUSED").param("reason", "Có giấy khám bệnh")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/teacher/sections/1"))
                    .andExpect(flash().attribute("successMessage", "Đã điều chỉnh điểm danh của 1 học sinh"));
            Attendance row = attendanceRepository.findBySessionId(1L).getFirst();
            assertThat(row.getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
            assertThat(row.getNote()).isNull();
            AuditLog audit = lastAudit(AuditActions.ADJUST_ATTENDANCE);
            assertThat(audit.getEntityId()).isEqualTo(row.getId());
            assertThat(audit.getDetail()).contains("student=HS260001", "status=PRESENT -> ABSENT_EXCUSED",
                    "reason=Có giấy khám bệnh");
            // Lớp 1 chỉ có 1 buổi: vắng 1/1 vừa vượt ngưỡng 20% → cảnh báo gửi SV + PH (giai đoạn 6a).
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.SEND_NOTIFICATION);
            assertThat(lastAudit().getDetail()).isEqualTo("target=USER:" + TestAccounts.STUDENT_ID
                    + ", recipients=2, automatic=true");
        }

        @Test
        @DisplayName("0900000003 vào lớp của giáo viên khác bằng URL → 403, không ghi gì")
        void teacherDeniedOtherSection() throws Exception {
            mockMvc.perform(get("/teacher/sections/27").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/teacher/sections/27/sessions/41/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(mark(27L, 41L, "statuses[2]", "ABSENT_UNEXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "x").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());

            assertThat(attendanceRepository.findBySessionId(41L)).extracting(Attendance::getStatus)
                    .containsOnly(AttendanceStatus.PRESENT);
        }

        @Test
        @DisplayName("sessionId không thuộc lớp {id} → 404")
        void sessionOfAnotherSection() throws Exception {
            mockMvc.perform(get("/teacher/sections/1/sessions/41/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(mark(1L, 41L, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Buổi chưa diễn ra không mở được bảng điểm danh")
        void futureSessionRejected() throws Exception {
            Long future = futureSession(1L);

            mockMvc.perform(get("/teacher/sections/1/sessions/" + future + "/attendance")
                            .with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/"))
                    .andExpect(flash().attribute("errorMessage", containsString("Buổi học chưa diễn ra")));
            mockMvc.perform(mark(1L, future, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/"));
            assertThat(attendanceRepository.findBySessionId(future)).isEmpty();
        }

        @Test
        @DisplayName("Trạng thái sửa tay không hợp lệ → hiện lại form, không ghi")
        void invalidStatus() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "HACKED").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Trạng thái điểm danh không hợp lệ")));
            assertThat(attendanceRepository.findBySessionId(1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ADMIN và tổ trưởng chuyên môn")
    class Managers {

        @Test
        @DisplayName("ADMIN điều chỉnh buổi đã điểm danh: audit có giá trị cũ và mới cho đúng học sinh bị đổi")
        void adminAdjusts() throws Exception {
            long adjustAuditsBefore = auditCount(AuditActions.ADJUST_ATTENDANCE);
            long notificationAuditsBefore = auditCount(AuditActions.SEND_NOTIFICATION);

            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "Có minh chứng đi viện").with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/27/attendance"));

            // Đúng một audit điều chỉnh (chỉ HS250101 bị đổi); HS250101 vắng 1/6 → 2/6 vừa vượt 20% nên có thêm
            // đúng một thông báo cảnh báo (giai đoạn 6a).
            assertThat(auditCount(AuditActions.ADJUST_ATTENDANCE)).isEqualTo(adjustAuditsBefore + 1);
            assertThat(auditCount(AuditActions.SEND_NOTIFICATION)).isEqualTo(notificationAuditsBefore + 1);
            AuditLog audit = lastAudit(AuditActions.ADJUST_ATTENDANCE);
            assertThat(audit.getUser().getId()).isEqualTo(TestAccounts.ADMIN_ID);
            assertThat(audit.getDetail()).contains("student=HS250101", "status=PRESENT -> ABSENT_EXCUSED",
                    "note=Đúng giờ -> Đúng giờ", "reason=Có minh chứng đi viện");
            assertThat(attendanceRepository.findBySessionId(41L))
                    .extracting(a -> a.getStudent().getId(), Attendance::getStatus, a -> a.getMarkedBy().getId())
                    .containsExactlyInAnyOrder(tuple(2L, AttendanceStatus.ABSENT_EXCUSED, TestAccounts.ADMIN_ID),
                            tuple(3L, AttendanceStatus.PRESENT, 16L));
        }

        @Test
        @DisplayName("ADMIN điều chỉnh thiếu lý do → lỗi cạnh ô lý do")
        void adminReasonRequired() throws Exception {
            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vui lòng nhập lý do điều chỉnh điểm danh")));
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn SE xem thống kê và bảng điểm danh lớp tổ chuyên môn mình, nhưng không lưu được (403)")
        void headViewsOnly() throws Exception {
            mockMvc.perform(get("/admin/sections/27/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("HS250101")));
            mockMvc.perform(get("/admin/sections/27/sessions/41/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("statuses[2]"))));
            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "x").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn gửi form sai định dạng vẫn nhận 403, không thấy form sửa được")
        void headMalformedPostIsForbidden() throws Exception {
            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "HACKED").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn xem lớp môn học thuộc tổ chuyên môn khác → 403")
        void headDeniedOtherDepartment() throws Exception {
            Long ia = iaSection();

            mockMvc.perform(get("/admin/sections/" + ia + "/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Học sinh, phụ huynh xem điểm danh")
    class Viewers {

        @Test
        @DisplayName("0900000004 xem điểm danh và tỷ lệ chuyên cần của mình")
        void studentSeesOwn() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)));

            mockMvc.perform(get("/student/attendance").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10"), containsString("100,0%"),
                            not(containsString("TIN11")))));
            mockMvc.perform(get("/student/attendance/1").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Có mặt")));
        }

        @Test
        @DisplayName("Học sinh mở lịch sử lớp mình không học → 403")
        void studentDeniedOtherSection() throws Exception {
            mockMvc.perform(get("/student/attendance/27").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("0900000005 xem điểm danh của con; con chưa liên kết → 403")
        void parentSeesLinkedChildOnly() throws Exception {
            mockMvc.perform(get("/parent/attendance").with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/parent/children/1/attendance"));
            mockMvc.perform(get("/parent/children/1/attendance").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("TOAN10")));
            mockMvc.perform(get("/parent/children/1/attendance/1").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/parent/children/2/attendance").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/2/attendance/27").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Vắng vượt 20% → cảnh báo ở trang học sinh, phụ huynh và bảng thống kê của giáo viên")
        void absenceWarningShown() throws Exception {
            // HS250101 ở lớp 27 (6 buổi chưa hủy) đã có 1 buổi vắng không phép (buổi 43); thêm 1 buổi vắng → 33,3%.
            mockMvc.perform(post("/admin/sections/27/sessions/42/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "Nghỉ ốm có giấy").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(get("/teacher/sections/27").with(as(GV003)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("33,3%"), containsString("Vắng quá 20%"))));
            mockMvc.perform(get("/student/attendance").with(as(HS250101)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vắng quá 20%")));
            mockMvc.perform(get("/parent/children/2/attendance").with(as(PARENT_OF_HS250101)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vắng quá 20%")));
        }
    }

    @Nested
    @DisplayName("Phân quyền theo vai trò")
    class Roles {

        @Test
        @DisplayName("Chưa đăng nhập → chuyển tới trang đăng nhập")
        void anonymousRedirectedToLogin() throws Exception {
            mockMvc.perform(get("/teacher/sections")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/student/attendance")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/parent/children/1/attendance")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/sections/1/attendance")).andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("Sai vai trò → 403")
        void wrongRoleForbidden() throws Exception {
            mockMvc.perform(get("/teacher/sections").with(as(TestAccounts.STUDENT))).andExpect(status().isForbidden());
            mockMvc.perform(get("/student/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/attendance").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder mark(Long sectionId, Long sessionId, String name, String value) {
        return post("/teacher/sections/" + sectionId + "/sessions/" + sessionId + "/attendance").with(csrf())
                .param(name, value);
    }

    private Long futureSession(Long sectionId) {
        ClassSection section = classSectionRepository.findById(sectionId).orElseThrow();
        ClassSession session = new ClassSession();
        session.setClassSection(section);
        session.setSessionNo((short) 99);
        session.setSessionDate(AppTime.today().plusDays(7));
        session.setStartTime(LocalTime.of(13, 0));
        session.setEndTime(LocalTime.of(15, 15));
        return classSessionRepository.saveAndFlush(session).getId();
    }

    private Long iaSection() {
        Subject subject = new Subject();
        subject.setDepartment(departmentRepository.findById(38L).orElseThrow());
        subject.setCode("IAO201");
        subject.setName("Nhập môn an toàn thông tin");
        subject.setCredits((short) 3);
        subjectRepository.saveAndFlush(subject);
        ClassSection section = new ClassSection();
        section.setCode("IAO201-HK1-2627-01");
        section.setSubject(subject);
        section.setSemester(semesterRepository.findById(1L).orElseThrow());
        section.setTeacher(teacherRepository.findById(2L).orElseThrow());
        return classSectionRepository.saveAndFlush(section).getId();
    }

    private AuditLog lastAudit(String action) {
        return auditLogRepository.findAll().stream().filter(a -> action.equals(a.getAction()))
                .max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    private long auditCount(String action) {
        return auditLogRepository.findAll().stream().filter(a -> action.equals(a.getAction())).count();
    }

    private AuditLog lastAudit() {
        List<AuditLog> all = auditLogRepository.findAll();
        return all.stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    @Nested
    @DisplayName("Giao diện trên điện thoại (review 8a, U1, U2)")
    class SmallScreens {

        @Test
        @DisplayName("Bảng điểm danh theo môn của HS, PH: dạng thẻ, mỗi ô có nhãn, chuyên cần và tỷ lệ vắng được ưu tiên")
        void subjectTableStacks() throws Exception {
            for (String url : List.of("/student/attendance", "/parent/children/1/attendance")) {
                String phone = url.startsWith("/student") ? TestAccounts.STUDENT : TestAccounts.PARENT;
                mockMvc.perform(get(url).with(as(phone)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(allOf(containsString("table-stack"),
                                containsString("data-label=\"Chuyên cần\""),
                                containsString("data-label=\"Tỷ lệ vắng\""),
                                containsString("data-label=\"Có mặt\""), containsString("stack-priority"))));
            }
        }

        @Test
        @DisplayName("Bảng điểm danh một buổi: dạng thẻ, trạng thái là nút lớn (btn-check), mỗi HS đúng 3 lựa chọn cùng tên")
        void sheetUsesButtons() throws Exception {
            String html = mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("table-stack"),
                            containsString("class=\"btn-check\""), containsString("attendance-choices"))))
                    .andReturn().getResponse().getContentAsString();

            // Một bản HTML duy nhất: không gửi trùng statuses[...] khi lưu.
            assertThat(html.split("name=\"statuses\\[1\\]\"", -1)).hasSize(4);
        }
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
