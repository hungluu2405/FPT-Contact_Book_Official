package vn.edu.fpt.contactbook.controller;

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
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
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
 * Điểm danh trên FptContactBook_test. Dữ liệu mẫu: lớp 1 (PRJ301-SE1901-FA26, GV002 = 0900000003, buổi 1 ngày
 * 08/09/2026 chưa điểm danh, có SE190001 = students.id 1); lớp 27 (PRN211, GV003 = 0922334455, buổi 41–46, buổi
 * 41 đã điểm danh PRESENT cho SE180101 (students.id 2) và SE180102 (students.id 3)). Phụ huynh 0900000005 liên kết
 * SE190001; 0944112233 liên kết SE180101. Trưởng bộ môn 0900000002 thuộc bộ môn SE.
 */
@IntegrationTest
class AttendanceIntegrationTest {

    static final String GV003 = "0922334455";
    static final String SE180101 = "0933112233";
    static final String PARENT_OF_SE180101 = "0944112233";

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

    @Nested
    @DisplayName("Giảng viên điểm danh")
    class TeacherMarks {

        @Test
        @DisplayName("0900000003 thấy lớp mình dạy và mở được bảng điểm danh buổi 1 (200)")
        void teacherOpensOwnSection() throws Exception {
            mockMvc.perform(get("/teacher/sections").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("PRJ301-SE1901-FA26"),
                            not(containsString("PRN211_FA26_01")))));
            mockMvc.perform(get("/teacher/sections/1").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("SE190001")));
            mockMvc.perform(get("/teacher/sections/1/sessions/1/attendance").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("SE190001"),
                            containsString("statuses[1]"), containsString("_csrf"))));
        }

        @Test
        @DisplayName("Lưu lần đầu: tạo dòng với marked_by là giảng viên, không cần lý do, audit MARK_ATTENDANCE")
        void teacherSavesFirstTime() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "LATE").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl("/teacher/sections/1"))
                    .andExpect(flash().attribute("successMessage", "Đã lưu điểm danh buổi 1 (1 sinh viên)"));

            assertThat(attendanceRepository.findBySessionId(1L))
                    .extracting(a -> a.getStudent().getId(), Attendance::getStatus, a -> a.getMarkedBy().getId(),
                            Attendance::getNote)
                    .containsExactly(tuple(1L, AttendanceStatus.LATE, TestAccounts.TEACHER_ID, null));
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.MARK_ATTENDANCE);
            assertThat(audit.getUser().getId()).isEqualTo(TestAccounts.TEACHER_ID);
            assertThat(audit.getDetail()).contains("LATE=1");
        }

        @Test
        @DisplayName("Điều chỉnh sau lần lưu đầu: thiếu lý do thì báo lỗi; có lý do thì audit cũ → mới")
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
                    .andExpect(flash().attribute("successMessage", "Đã điều chỉnh điểm danh của 1 sinh viên"));
            Attendance row = attendanceRepository.findBySessionId(1L).getFirst();
            assertThat(row.getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
            assertThat(row.getNote()).isEqualTo("Có giấy khám bệnh");
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ADJUST_ATTENDANCE);
            assertThat(audit.getEntityId()).isEqualTo(row.getId());
            assertThat(audit.getDetail()).contains("student=SE190001", "status=PRESENT -> ABSENT_EXCUSED",
                    "reason=Có giấy khám bệnh");
        }

        @Test
        @DisplayName("0900000003 vào lớp của giảng viên khác bằng URL → 403, không ghi gì")
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
    @DisplayName("ADMIN và trưởng bộ môn")
    class Managers {

        @Test
        @DisplayName("ADMIN điều chỉnh buổi đã điểm danh: audit có giá trị cũ và mới cho đúng sinh viên bị đổi")
        void adminAdjusts() throws Exception {
            long before = auditLogRepository.count();

            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "Có minh chứng đi viện").with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/27/attendance"));

            assertThat(auditLogRepository.count()).isEqualTo(before + 1);
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ADJUST_ATTENDANCE);
            assertThat(audit.getUser().getId()).isEqualTo(TestAccounts.ADMIN_ID);
            assertThat(audit.getDetail()).contains("student=SE180101", "status=PRESENT -> ABSENT_EXCUSED",
                    "oldNote=Đúng giờ", "reason=Có minh chứng đi viện");
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
        @DisplayName("Trưởng bộ môn SE xem thống kê và bảng điểm danh lớp bộ môn mình, nhưng không lưu được (403)")
        void headViewsOnly() throws Exception {
            mockMvc.perform(get("/admin/sections/27/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("SE180101")));
            mockMvc.perform(get("/admin/sections/27/sessions/41/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("statuses[2]"))));
            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "x").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Trưởng bộ môn gửi form sai định dạng vẫn nhận 403, không thấy form sửa được")
        void headMalformedPostIsForbidden() throws Exception {
            mockMvc.perform(post("/admin/sections/27/sessions/41/attendance").with(csrf())
                            .param("statuses[2]", "HACKED").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Trưởng bộ môn xem lớp học phần thuộc bộ môn khác → 403")
        void headDeniedOtherDepartment() throws Exception {
            Long ia = iaSection();

            mockMvc.perform(get("/admin/sections/" + ia + "/attendance").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Sinh viên, phụ huynh xem điểm danh")
    class Viewers {

        @Test
        @DisplayName("0900000004 xem điểm danh và tỷ lệ chuyên cần của mình")
        void studentSeesOwn() throws Exception {
            mockMvc.perform(mark(1L, 1L, "statuses[1]", "PRESENT").with(as(TestAccounts.TEACHER)));

            mockMvc.perform(get("/student/attendance").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("PRJ301"), containsString("100,0%"),
                            not(containsString("PRN211")))));
            mockMvc.perform(get("/student/attendance/1").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Có mặt")));
        }

        @Test
        @DisplayName("Sinh viên mở lịch sử lớp mình không học → 403")
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
                    .andExpect(content().string(containsString("PRJ301")));
            mockMvc.perform(get("/parent/children/1/attendance/1").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/parent/children/2/attendance").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/2/attendance/27").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Vắng vượt 20% → cảnh báo ở trang sinh viên, phụ huynh và bảng thống kê của giảng viên")
        void absenceWarningShown() throws Exception {
            // SE180101 ở lớp 27 (6 buổi chưa hủy) đã có 1 buổi vắng không phép (buổi 43); thêm 1 buổi vắng → 33,3%.
            mockMvc.perform(post("/admin/sections/27/sessions/42/attendance").with(csrf())
                            .param("statuses[2]", "ABSENT_EXCUSED").param("statuses[3]", "PRESENT")
                            .param("reason", "Nghỉ ốm có giấy").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().is3xxRedirection());

            mockMvc.perform(get("/teacher/sections/27").with(as(GV003)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("33,3%"), containsString("Vắng quá 20%"))));
            mockMvc.perform(get("/student/attendance").with(as(SE180101)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vắng quá 20%")));
            mockMvc.perform(get("/parent/children/2/attendance").with(as(PARENT_OF_SE180101)))
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
        section.setCode("IAO201-FA26-01");
        section.setSubject(subject);
        section.setSemester(semesterRepository.findById(1L).orElseThrow());
        section.setTeacher(teacherRepository.findById(2L).orElseThrow());
        return classSectionRepository.saveAndFlush(section).getId();
    }

    private AuditLog lastAudit() {
        List<AuditLog> all = auditLogRepository.findAll();
        return all.stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
