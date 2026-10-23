package vn.edu.fpt.contactbook.controller;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.UserImportWorkbooks;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Báo cáo trên FptContactBook_test. Dữ liệu mẫu học kỳ HK1 (id 1), mọi môn thuộc bộ môn SE (id 1):
 * lớp 1 TOAN10-HK1-2627-01 (GV002 = 0900000003, chỉ HS260001); lớp 27 TIN11-HK1-2627-01 (GV003, 2 SV ENROLLED, có điểm
 * danh và 4 ô điểm, chưa ai đủ điểm); lớp 28 STEM-HK1-2627-01 (GV004). Trưởng bộ môn 0900000002 thuộc SE.
 * Lớp của bộ môn IA tạo trong test (rollback sau mỗi test).
 */
@IntegrationTest
class ReportIntegrationTest {

    static final String ATTENDANCE = "/reports/attendance";
    static final String GRADES = "/reports/grades";
    static final String CLUBS = "/reports/clubs";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;

    @Nested
    @DisplayName("Phạm vi xem báo cáo")
    class Scope {

        @Test
        @DisplayName("GV xem báo cáo lớp mình dạy; danh sách tổng hợp không có lớp người khác")
        void teacherOwnSection() throws Exception {
            for (String report : List.of(ATTENDANCE, GRADES)) {
                mockMvc.perform(get(report).param("sectionId", "1").with(as(TestAccounts.TEACHER)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("TOAN10-HK1-2627-01")));
                mockMvc.perform(get(report).with(as(TestAccounts.TEACHER)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(allOf(containsString("TOAN10-HK1-2627-01"),
                                not(containsString("TIN11-HK1-2627-01")), not(containsString("STEM-HK1-2627-01")))));
            }
        }

        @Test
        @DisplayName("GV sửa tham số sang lớp khác, môn không dạy hoặc id không tồn tại (xem và xuất) → 403")
        void teacherTamperedParameters() throws Exception {
            for (String report : List.of(ATTENDANCE, GRADES)) {
                for (String url : List.of(report, report + "/export")) {
                    mockMvc.perform(get(url).param("sectionId", "27").with(as(TestAccounts.TEACHER)))
                            .andExpect(status().isForbidden());
                    mockMvc.perform(get(url).param("sectionId", "999999").with(as(TestAccounts.TEACHER)))
                            .andExpect(status().isForbidden());
                    mockMvc.perform(get(url).param("subjectId", "31").with(as(TestAccounts.TEACHER)))
                            .andExpect(status().isForbidden());
                    // Kèm cả học kỳ, môn hợp lệ: sectionId vẫn được kiểm tra.
                    mockMvc.perform(get(url).param("semesterId", "1").param("subjectId", "1")
                                    .param("sectionId", "28").with(as(TestAccounts.TEACHER)))
                            .andExpect(status().isForbidden());
                }
            }
        }

        @Test
        @DisplayName("TBM xem mọi lớp bộ môn mình (kể cả lớp người khác dạy); lớp, môn bộ môn khác → 403")
        void headDepartment() throws Exception {
            ClassSection ia = iaSection();
            for (String report : List.of(ATTENDANCE, GRADES)) {
                mockMvc.perform(get(report).param("sectionId", "27").with(as(TestAccounts.TEACHER_HEAD)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("TIN11-HK1-2627-01")));
                mockMvc.perform(get(report).with(as(TestAccounts.TEACHER_HEAD)))
                        .andExpect(content().string(allOf(containsString("TIN11-HK1-2627-01"),
                                containsString("STEM-HK1-2627-01"), not(containsString(ia.getCode())))));
                for (String url : List.of(report, report + "/export")) {
                    mockMvc.perform(get(url).param("sectionId", ia.getId().toString())
                            .with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isForbidden());
                    mockMvc.perform(get(url).param("subjectId", ia.getSubject().getId().toString())
                            .with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isForbidden());
                }
            }
        }

        @Test
        @DisplayName("ADMIN xem mọi lớp; id không tồn tại → 404")
        void admin() throws Exception {
            ClassSection ia = iaSection();
            mockMvc.perform(get(ATTENDANCE).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(ia.getCode())));
            mockMvc.perform(get(GRADES).param("sectionId", ia.getId().toString()).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk());
            mockMvc.perform(get(ATTENDANCE).param("sectionId", "999999").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("SINH VIÊN, PHỤ HUYNH vào mọi trang báo cáo → 403")
        void studentParentForbidden() throws Exception {
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.PARENT)) {
                for (String url : List.of("/reports", ATTENDANCE, ATTENDANCE + "/export", GRADES, GRADES + "/export",
                        CLUBS)) {
                    mockMvc.perform(get(url).with(as(phone))).andExpect(status().isForbidden());
                }
            }
        }

        @Test
        @DisplayName("Báo cáo CLB chỉ ADMIN: GV, TBM → 403")
        void clubsAdminOnly() throws Exception {
            mockMvc.perform(get(CLUBS).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("F-CODE")));
            for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.TEACHER_HEAD)) {
                mockMvc.perform(get(CLUBS).with(as(phone))).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Chưa đăng nhập → /login")
        void anonymous() throws Exception {
            for (String url : List.of("/reports", ATTENDANCE, ATTENDANCE + "/export", GRADES, GRADES + "/export",
                    CLUBS)) {
                mockMvc.perform(get(url)).andExpect(redirectedUrl("/login"));
            }
        }
    }

    @Nested
    @DisplayName("Xuất Excel")
    class Export {

        @Test
        @DisplayName("Điểm danh một lớp: đúng content type, mở lại được bằng POI, đủ sinh viên, ghi EXPORT_REPORT")
        void attendanceSection() throws Exception {
            MvcResult result = mockMvc.perform(get(ATTENDANCE + "/export").param("sectionId", "27")
                            .with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(UserImportWorkbooks.CONTENT_TYPE))
                    .andExpect(header().string("Content-Disposition",
                            containsString("bao-cao-diem-danh-HK1-2627-TIN11-HK1-2627-01.xlsx")))
                    .andReturn();

            try (XSSFWorkbook workbook = open(result)) {
                Sheet sheet = workbook.getSheet("Sinh viên");
                assertThat(sheet).isNotNull();
                assertThat(column(sheet, "Mã SV")).hasSize(2).allSatisfy(code -> assertThat(code).startsWith("HS"));
            }
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.EXPORT_REPORT);
            assertThat(audit.getUser().getId()).isEqualTo(TestAccounts.ADMIN_ID);
            assertThat(audit.getEntityType()).isEqualTo(AuditActions.ENTITY_CLASS_SECTION);
            assertThat(audit.getEntityId()).isEqualTo(27L);
            assertThat(audit.getDetail()).isEqualTo(
                    "report=ATTENDANCE, semester=HK1-2627, subject=TIN11, section=TIN11-HK1-2627-01, rows=2");
            assertThat(audit.getIpAddress()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Điểm cả học kỳ của TBM: chỉ lớp trong bộ môn, mở lại được; GV chỉ xuất được lớp mình")
        void gradesOverviewInScope() throws Exception {
            ClassSection ia = iaSection();
            MvcResult head = mockMvc.perform(get(GRADES + "/export").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(UserImportWorkbooks.CONTENT_TYPE))
                    .andReturn();
            try (XSSFWorkbook workbook = open(head)) {
                assertThat(column(workbook.getSheet("Lớp học phần"), "Lớp học phần"))
                        .contains("TOAN10-HK1-2627-01", "TIN11-HK1-2627-01", "STEM-HK1-2627-01")
                        .doesNotContain(ia.getCode());
            }
            assertThat(lastAudit().getDetail()).startsWith("report=GRADES, semester=HK1-2627, subject=-, section=-");

            MvcResult teacher = mockMvc.perform(get(GRADES + "/export").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk()).andReturn();
            try (XSSFWorkbook workbook = open(teacher)) {
                assertThat(column(workbook.getSheet("Lớp học phần"), "Lớp học phần"))
                        .containsExactly("TOAN10-HK1-2627-01");
            }
        }

        @Test
        @DisplayName("Điểm một lớp: sheet sinh viên (chưa đủ điểm ghi rõ) và sheet thống kê")
        void gradesSection() throws Exception {
            MvcResult result = mockMvc.perform(get(GRADES + "/export").param("sectionId", "27")
                            .with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk()).andReturn();

            try (XSSFWorkbook workbook = open(result)) {
                assertThat(column(workbook.getSheet("Sinh viên"), "Trung bình môn"))
                        .containsExactly("Chưa đủ điểm", "Chưa đủ điểm");
                assertThat(workbook.getSheet("Thống kê")).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Nội dung trang")
    class Pages {

        @Test
        @DisplayName("Biểu đồ dùng Chart.js từ WebJars, dữ liệu in trong trang; mã lớp có </script> được escape")
        void chartEscaped() throws Exception {
            ClassSection section = iaSection();
            section.setCode("X</SCRIPT><B>");
            classSectionRepository.saveAndFlush(section);

            mockMvc.perform(get(ATTENDANCE).with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("/webjars/chart.js/4.4.1/dist/chart.umd.js"),
                            containsString("window.reportChart"), not(containsString("</SCRIPT><B>")),
                            not(containsString("cdn.")))));
        }

        @Test
        @DisplayName("Báo cáo điểm một lớp chưa ai đủ điểm: hiện số chưa đủ điểm, không có phổ điểm để vẽ")
        void gradesIncomplete() throws Exception {
            mockMvc.perform(get(GRADES).param("sectionId", "27").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Chưa đủ điểm"), containsString("8,50"),
                            not(containsString("window.reportChart")))));
        }

        @Test
        @DisplayName("Menu: Báo cáo cho GV, TBM, ADMIN; Nhật ký hệ thống chỉ ADMIN")
        void menu() throws Exception {
            mockMvc.perform(get("/profile").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("href=\"/reports/attendance\""),
                            containsString("href=\"/admin/audit-logs\""))));
            for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.TEACHER_HEAD)) {
                mockMvc.perform(get("/profile").with(as(phone)))
                        .andExpect(content().string(allOf(containsString("href=\"/reports/attendance\""),
                                not(containsString("href=\"/admin/audit-logs\"")))));
            }
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.PARENT)) {
                mockMvc.perform(get("/profile").with(as(phone)))
                        .andExpect(content().string(allOf(not(containsString("href=\"/reports/attendance\"")),
                                not(containsString("href=\"/admin/audit-logs\"")))));
            }
        }
    }

    /** Lớp của bộ môn IA (id 38), do GV003 (teachers.id 8) dạy, học kỳ HK1. */
    private ClassSection iaSection() {
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
        section.setTeacher(teacherRepository.findById(8L).orElseThrow());
        return classSectionRepository.saveAndFlush(section);
    }

    private static XSSFWorkbook open(MvcResult result) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    /** Giá trị dạng chữ của một cột, từ sau dòng tên cột tới hết sheet (bỏ dòng "Tổng"). */
    private static List<String> column(Sheet sheet, String header) {
        int headerRow = -1;
        int index = -1;
        for (Row row : sheet) {
            for (var cell : row) {
                if (header.equals(cell.toString())) {
                    headerRow = row.getRowNum();
                    index = cell.getColumnIndex();
                }
            }
            if (index >= 0) {
                break;
            }
        }
        assertThat(index).as("cột %s", header).isNotNegative();
        List<String> values = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String value = row == null || row.getCell(index) == null ? null : row.getCell(index).toString();
            if (value != null && !value.equals("Tổng")) {
                values.add(value);
            }
        }
        return values;
    }

    private AuditLog lastAudit() {
        return auditLogRepository.findAll().stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
