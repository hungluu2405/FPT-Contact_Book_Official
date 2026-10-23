package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Exam;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.ExamType;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.time.LocalDate;
import java.time.LocalTime;

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
 * Buổi học, lịch thi và trang xem lịch trên FptContactBook_test. Dữ liệu mẫu: lớp 1 (TOAN10, GV002, phòng R101,
 * buổi 08/09/2026, có HS260001); lớp 27 (TIN11, GV003, phòng 14, buổi 05–21/10/2026, có HS250101 và HS250102);
 * lớp 28 (STEM) có chung HS250101 với lớp 27. Phụ huynh 0900000005 liên kết với HS260001 (students.id 1).
 */
@IntegrationTest
class ScheduleIntegrationTest {

    static final LocalDate OCT19 = LocalDate.of(2026, 10, 19);

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ClassSessionRepository classSessionRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired ExamRepository examRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("Quản lý buổi học")
    class ManageSessions {

        @Test
        @DisplayName("ADMIN tạo lịch hàng tuần cho lớp mẫu: đúng số buổi, đúng thứ, ghi audit")
        void adminCreatesWeeklySchedule() throws Exception {
            mockMvc.perform(weekly(1L, "2026-11-02", "4").with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/1"))
                    .andExpect(flash().attribute("successMessage", "Đã tạo 4 buổi học"));

            assertThat(classSessionRepository.findByClassSectionIdOrderBySessionNoAsc(1L))
                    .filteredOn(s -> s.getSessionDate().isAfter(LocalDate.of(2026, 11, 1)))
                    .extracting(ClassSession::getSessionDate, ClassSession::getSessionNo, ClassSession::getStatus)
                    .containsExactly(
                            tuple(LocalDate.of(2026, 11, 2), (short) 2, SessionStatus.SCHEDULED),
                            tuple(LocalDate.of(2026, 11, 4), (short) 3, SessionStatus.SCHEDULED),
                            tuple(LocalDate.of(2026, 11, 9), (short) 4, SessionStatus.SCHEDULED),
                            tuple(LocalDate.of(2026, 11, 11), (short) 5, SessionStatus.SCHEDULED));
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_WEEKLY_SESSIONS);
        }

        @Test
        @DisplayName("Buổi trùng phòng 14 (giờ chồng một phần với TIN11) → từ chối, bảng xung đột tiếng Việt")
        void roomConflictPartialOverlap() throws Exception {
            mockMvc.perform(single(1L, OCT19, "09:00", "11:00", "14").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Trùng phòng"),
                            containsString("TIN11-HK1-2627-01"), containsString("07:30–09:50"),
                            containsString("chưa lưu gì"))));
            assertThat(classSessionRepository.findByClassSectionIdAndSessionDateBetween(1L, OCT19, OCT19)).isEmpty();
        }

        @Test
        @DisplayName("Buổi trùng giảng viên GV003 (đang dạy TIN11 lúc đó) → từ chối")
        void teacherConflict() throws Exception {
            ClassSection sameTeacher = section("TOAN10-HK1-2627-77", subjectRepository.findById(1L).orElseThrow(), 8L);

            mockMvc.perform(single(sameTeacher.getId(), OCT19, "08:00", "09:00", null).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Trùng giảng viên"),
                            containsString("Giảng viên đang dạy lớp TIN11-HK1-2627-01"))));
        }

        @Test
        @DisplayName("Sinh viên HS250101 học cả STEM và TIN11 → buổi STEM chồng giờ bị từ chối")
        void studentConflict() throws Exception {
            mockMvc.perform(single(28L, OCT19, "08:00", "09:00", null).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Trùng sinh viên"),
                            containsString("1 sinh viên của lớp này cũng học lớp TIN11-HK1-2627-01"))));
        }

        @Test
        @DisplayName("Lịch tuần có một buổi trùng → không lưu buổi nào")
        void weeklyWithOneConflictSavesNothing() throws Exception {
            long before = classSessionRepository.count();

            mockMvc.perform(post("/admin/sections/1/sessions/weekly").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("days", "MONDAY").param("startTime", "09:00").param("endTime", "10:00")
                            .param("roomId", "14").param("fromDate", "2026-10-12").param("count", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Trùng phòng"), containsString("12/10/2026"),
                            containsString("19/10/2026"))));
            assertThat(classSessionRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("Buổi bù hợp lệ: MAKEUP; hủy buổi: CANCELLED, vẫn còn dòng")
        void makeupThenCancel() throws Exception {
            mockMvc.perform(single(1L, LocalDate.of(2026, 10, 24), "13:00", "15:15", "1").with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/1"));
            ClassSession makeup = classSessionRepository.findByClassSectionIdAndSessionDateBetween(1L,
                    LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24)).getFirst();
            assertThat(makeup.getStatus()).isEqualTo(SessionStatus.MAKEUP);

            mockMvc.perform(post("/admin/sections/1/sessions/" + makeup.getId() + "/cancel")
                            .with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã hủy buổi học"));
            assertThat(classSessionRepository.findById(makeup.getId())).get()
                    .extracting(ClassSession::getStatus).isEqualTo(SessionStatus.CANCELLED);
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CANCEL_SESSION);
        }

        @Test
        @DisplayName("Buổi 41 đã điểm danh: không hủy được, không đổi giờ được")
        void attendedSessionProtected() throws Exception {
            mockMvc.perform(post("/admin/sections/27/sessions/41/cancel").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Buổi học đã có điểm danh nên không hủy được"));
            mockMvc.perform(post("/admin/sections/27/sessions/41/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("sessionDate", "2026-10-05").param("startTime", "13:00").param("endTime", "15:00")
                            .param("roomId", "14"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("chỉ đổi được phòng và ghi chú")));
        }

        @Test
        @DisplayName("Lịch thi trùng phòng với buổi học → từ chối; lịch thi hợp lệ hiện cho sinh viên")
        void exams() throws Exception {
            mockMvc.perform(post("/admin/sections/1/exams/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("examType", "MIDTERM").param("examDate", "2026-10-19").param("startTime", "08:00")
                            .param("endTime", "09:00").param("roomId", "14"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Trùng phòng")));

            mockMvc.perform(post("/admin/sections/1/exams/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("examType", "FINAL").param("examDate", "2026-12-15").param("startTime", "08:00")
                            .param("endTime", "09:30").param("roomId", "1"))
                    .andExpect(redirectedUrl("/admin/sections/1"));
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_EXAM);
            mockMvc.perform(get("/student/exams").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(allOf(containsString("15/12/2026"), containsString("Cuối kỳ"))));
        }
    }

    @Nested
    @DisplayName("Phân quyền quản lý")
    class ManageAccess {

        @Test
        @DisplayName("Trưởng bộ môn SE quản lý được buổi của lớp bộ môn mình")
        void headManagesOwnDepartment() throws Exception {
            mockMvc.perform(get("/admin/sections/1/sessions/weekly").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk());
            mockMvc.perform(single(1L, LocalDate.of(2026, 10, 24), "13:00", "15:15", null)
                            .with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(redirectedUrl("/admin/sections/1"));
        }

        @Test
        @DisplayName("Trưởng bộ môn SE với lớp bộ môn IA (gõ thẳng URL) → 403, không lưu")
        void headForbiddenOnOtherDepartment() throws Exception {
            Long ia = iaSection();
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);
            mockMvc.perform(get("/admin/sections/" + ia + "/sessions/new").with(head)).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/" + ia + "/sessions/weekly").with(head))
                    .andExpect(status().isForbidden());
            mockMvc.perform(single(ia, LocalDate.of(2026, 10, 24), "13:00", "15:15", null).with(head))
                    .andExpect(status().isForbidden());
            mockMvc.perform(weekly(ia, "2026-11-02", "2").with(head)).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/" + ia + "/exams/new").with(head)).andExpect(status().isForbidden());
            assertThat(classSessionRepository.findByClassSectionIdOrderBySessionNoAsc(ia)).isEmpty();
        }

        @Test
        @DisplayName("TBM SE sửa, hủy buổi học và sửa, xóa lịch thi của lớp bộ môn IA → 403, dữ liệu không đổi "
                + "(review 8a, T3)")
        void headCannotChangeOtherDepartmentSchedule() throws Exception {
            ClassSection ia = classSectionRepository.findById(iaSection()).orElseThrow();
            ClassSession session = new ClassSession();
            session.setClassSection(ia);
            session.setSessionNo((short) 1);
            session.setSessionDate(LocalDate.of(2026, 11, 2));
            session.setStartTime(LocalTime.of(7, 30));
            session.setEndTime(LocalTime.of(9, 45));
            session = classSessionRepository.saveAndFlush(session);
            Exam exam = new Exam();
            exam.setClassSection(ia);
            exam.setExamType(ExamType.FINAL);
            exam.setExamDate(LocalDate.of(2026, 12, 16));
            exam.setStartTime(LocalTime.of(8, 0));
            exam.setEndTime(LocalTime.of(9, 30));
            exam = examRepository.saveAndFlush(exam);
            String base = "/admin/sections/" + ia.getId();
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);

            mockMvc.perform(get(base + "/sessions/" + session.getId() + "/edit").with(head))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/sessions/" + session.getId() + "/edit").with(head).with(csrf())
                            .param("sessionDate", "2026-11-03").param("startTime", "13:00").param("endTime", "15:15"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/sessions/" + session.getId() + "/cancel").with(head).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(base + "/exams/" + exam.getId() + "/edit").with(head))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/exams/" + exam.getId() + "/edit").with(head).with(csrf())
                            .param("examType", "MIDTERM").param("examDate", "2026-12-17").param("startTime", "13:00")
                            .param("endTime", "14:00"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/exams/" + exam.getId() + "/delete").with(head).with(csrf()))
                    .andExpect(status().isForbidden());

            ClassSession sessionAfter = classSessionRepository.findById(session.getId()).orElseThrow();
            assertThat(sessionAfter.getSessionDate()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(sessionAfter.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
            Exam examAfter = examRepository.findById(exam.getId()).orElseThrow();
            assertThat(examAfter.getExamType()).isEqualTo(ExamType.FINAL);
            assertThat(examAfter.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 16));
        }

        @ParameterizedTest
        @ValueSource(strings = {TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
        @DisplayName("Giảng viên, sinh viên, phụ huynh POST buổi học / lịch thi → 403")
        void othersCannotManage(String phone) throws Exception {
            mockMvc.perform(single(1L, LocalDate.of(2026, 10, 24), "13:00", "15:15", null).with(as(phone)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/sections/1/exams/new").with(as(phone)).with(csrf())
                    .param("examType", "FINAL")).andExpect(status().isForbidden());
        }

        @Test
        void anonymousGoesToLogin() throws Exception {
            mockMvc.perform(get("/admin/sections/1/sessions/new")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/student/schedule")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/parent/children/1/schedule")).andExpect(redirectedUrl("/login"));
        }
    }

    @Nested
    @DisplayName("Xem lịch")
    class ViewSchedule {

        @Test
        @DisplayName("Sinh viên 0900000004 xem lịch tuần và danh sách của mình (TOAN10), không thấy lớp khác")
        void studentSeesOwnSchedule() throws Exception {
            mockMvc.perform(get("/student/schedule").param("week", "2026-09-08").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10"), containsString("07:30–09:45"),
                            containsString("07/09/2026"), not(containsString("TIN11")))));
            mockMvc.perform(get("/student/schedule").param("view", "list").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("08/09/2026"), containsString("R101"))));
        }

        @Test
        @DisplayName("Phụ huynh 0900000005: /parent/schedule chuyển tới con; xem được lịch con; con khác → 403")
        void parentSeesLinkedChildOnly() throws Exception {
            mockMvc.perform(get("/parent/schedule").with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/parent/children/1/schedule"));
            mockMvc.perform(get("/parent/children/1/schedule").param("week", "2026-09-08")
                            .with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10"), containsString("HS260001"))));
            mockMvc.perform(get("/parent/children/2/schedule").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/2/exams").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/1/exams").with(as(TestAccounts.PARENT))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Giảng viên GV002 (0900000003) xem lịch dạy: có TOAN10, không có TIN11 của GV003")
        void teacherSeesOwnTeaching() throws Exception {
            mockMvc.perform(get("/teacher/schedule").param("week", "2026-09-08").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10"), not(containsString("TIN11")))));
            mockMvc.perform(get("/teacher/exams").with(as(TestAccounts.TEACHER))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Buổi hủy và buổi bù được đánh dấu trên lịch")
        void cancelledAndMakeupMarked() throws Exception {
            mockMvc.perform(single(1L, LocalDate.of(2026, 9, 10), "13:00", "15:15", null).with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/sections/1"));
            ClassSession seeded = classSessionRepository.findById(1L).orElseThrow();
            seeded.setStatus(SessionStatus.CANCELLED);
            classSessionRepository.flush();

            mockMvc.perform(get("/student/schedule").param("week", "2026-09-08").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(allOf(containsString("schedule-cancelled"),
                            containsString("schedule-makeup"), containsString("Học bù"), containsString("Đã hủy"))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/teacher/schedule", "/parent/schedule"})
        void studentCannotOpenOtherRolePages(String path) throws Exception {
            mockMvc.perform(get(path).with(as(TestAccounts.STUDENT))).andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder single(Long sectionId, LocalDate date, String start, String end,
                                                 String roomId) {
        MockHttpServletRequestBuilder request = post("/admin/sections/" + sectionId + "/sessions/new").with(csrf())
                .param("sessionDate", date.toString()).param("startTime", start).param("endTime", end);
        return roomId == null ? request : request.param("roomId", roomId);
    }

    private MockHttpServletRequestBuilder weekly(Long sectionId, String from, String count) {
        return post("/admin/sections/" + sectionId + "/sessions/weekly").with(csrf())
                .param("days", "MONDAY", "WEDNESDAY").param("startTime", "07:30").param("endTime", "09:45")
                .param("roomId", "1").param("fromDate", from).param("count", count);
    }

    private ClassSection section(String code, Subject subject, Long teacherId) {
        ClassSection section = new ClassSection();
        section.setCode(code);
        section.setSubject(subject);
        section.setSemester(semesterRepository.findById(1L).orElseThrow());
        section.setTeacher(teacherRepository.findById(teacherId).orElseThrow());
        return classSectionRepository.saveAndFlush(section);
    }

    private Long iaSection() {
        Subject subject = new Subject();
        subject.setDepartment(departmentRepository.findById(38L).orElseThrow());
        subject.setCode("IAO201");
        subject.setName("Nhập môn an toàn thông tin");
        subject.setCredits((short) 3);
        subjectRepository.saveAndFlush(subject);
        return section("IAO201-HK1-2627-01", subject, 2L).getId();
    }

    private AuditLog lastAudit() {
        return auditLogRepository.findAll().stream()
                .max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
