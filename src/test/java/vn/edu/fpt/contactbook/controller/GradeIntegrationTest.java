package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.ImportFiles;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Điểm số trên FptContactBook_test. Dữ liệu mẫu: lớp 1 (PRJ301-SE1901-FA26, GV002 = 0900000003, chỉ có SE190001 với
 * enrollments.id 1), môn PRJ301 có đầu điểm 1 Assignment 20%, 2 Midterm 30%, 3 Final 50% (đủ 100%), chưa có điểm.
 * Phụ huynh 0900000005 liên kết SE190001 (students.id 1); trưởng bộ môn 0900000002 thuộc bộ môn SE.
 * Trung bình khi nhập 8 / 7 / 9 là (8×20 + 7×30 + 9×50) / 100 = 8,2.
 */
@IntegrationTest
class GradeIntegrationTest {

    static final String SHEET = "/teacher/sections/1/grades";
    static final String MANAGE = "/admin/sections/1/grades";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired GradeRepository gradeRepository;
    @Autowired GradeComponentRepository gradeComponentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;

    @Nested
    @DisplayName("Nhập, công bố, xem")
    class Flow {

        @Test
        @DisplayName("Trước khi công bố SV và PH không thấy điểm; 0900000002 công bố xong thì thấy")
        void hiddenUntilPublished() throws Exception {
            mockMvc.perform(get(SHEET).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Assignment"), containsString("scores[1_1]"))));
            mockMvc.perform(enter("8", "7", "9").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl(SHEET))
                    .andExpect(flash().attribute("successMessage", "Đã lưu 3 ô điểm"));
            assertThat(grades()).extracting(g -> g.getComponent().getId(), Grade::getScore, Grade::isPublished,
                            g -> g.getGradedBy().getId())
                    .containsExactly(tuple(1L, new BigDecimal("8.00"), false, TestAccounts.TEACHER_ID),
                            tuple(2L, new BigDecimal("7.00"), false, TestAccounts.TEACHER_ID),
                            tuple(3L, new BigDecimal("9.00"), false, TestAccounts.TEACHER_ID));
            mockMvc.perform(get(SHEET).with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(containsString("8,2")));

            mockMvc.perform(get("/student/grades").with(as(TestAccounts.STUDENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("PRJ301"), containsString("Chưa đủ điểm"),
                            not(containsString("8,2")))));
            mockMvc.perform(get("/parent/children/1/grades").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Chưa đủ điểm"), not(containsString("8,2")))));

            mockMvc.perform(post(MANAGE + "/publish").with(csrf()).with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(redirectedUrl(MANAGE))
                    .andExpect(flash().attribute("successMessage", "Đã công bố 3 ô điểm"));
            assertThat(grades()).allSatisfy(g -> {
                assertThat(g.isPublished()).isTrue();
                assertThat(g.getPublishedBy().getId()).isEqualTo(TestAccounts.TEACHER_HEAD_ID);
                assertThat(g.getPublishedAt()).isNotNull();
            });
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.PUBLISH_GRADE);
            assertThat(audit.getDetail()).contains("count=3");

            mockMvc.perform(get("/student/grades").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(allOf(containsString("8,2"), not(containsString("Chưa đủ điểm")))));
            mockMvc.perform(get("/parent/children/1/grades").with(as(TestAccounts.PARENT)))
                    .andExpect(content().string(containsString("8,2")));
        }

        @Test
        @DisplayName("Công bố bỏ qua ô trống: ô nhập sau vẫn chưa công bố, SV vẫn thấy Chưa đủ điểm")
        void publishSkipsBlankCells() throws Exception {
            mockMvc.perform(enter("8", "7", "").with(as(TestAccounts.TEACHER))).andExpect(status().is3xxRedirection());
            mockMvc.perform(post(MANAGE + "/publish").with(csrf()).with(as(TestAccounts.ADMIN)))
                    .andExpect(flash().attribute("successMessage", "Đã công bố 2 ô điểm"));

            mockMvc.perform(post(SHEET).with(csrf()).param("scores[1_3]", "9").with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl(SHEET));

            assertThat(grades()).extracting(g -> g.getComponent().getId(), Grade::isPublished)
                    .containsExactly(tuple(1L, true), tuple(2L, true), tuple(3L, false));
            mockMvc.perform(get("/student/grades").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(allOf(containsString("Chưa đủ điểm"), not(containsString("8,2")))));
        }

        @Test
        @DisplayName("Điểm vượt max_score: lỗi cạnh ô, không lưu ô nào")
        void invalidScore() throws Exception {
            mockMvc.perform(enter("8", "11", "9").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Điểm phải từ 0 đến 10"),
                            containsString("is-invalid"))));
            assertThat(grades()).isEmpty();
        }

        @Test
        @DisplayName("Tổng trọng số khác 100%: không nhập và không công bố được")
        void weightsNot100() throws Exception {
            mockMvc.perform(enter("8", "7", "9").with(as(TestAccounts.TEACHER))).andExpect(status().is3xxRedirection());
            GradeComponent fin = gradeComponentRepository.findById(3L).orElseThrow();
            fin.setWeight(new BigDecimal("40.00"));
            gradeComponentRepository.saveAndFlush(fin);

            mockMvc.perform(enter("9", "7", "9").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("đang là 90%")));
            mockMvc.perform(post(MANAGE + "/publish").with(csrf()).with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(redirectedUrl(MANAGE))
                    .andExpect(flash().attribute("errorMessage", containsString("đang là 90%")));

            assertThat(grades()).extracting(Grade::getScore, Grade::isPublished).containsExactly(
                    tuple(new BigDecimal("8.00"), false), tuple(new BigDecimal("7.00"), false),
                    tuple(new BigDecimal("9.00"), false));
        }
    }

    @Nested
    @DisplayName("Điều chỉnh sau công bố")
    class Adjust {

        @Test
        @DisplayName("GV không sửa được ô đã công bố; TBM điều chỉnh có lý do, audit giá trị cũ và mới")
        void adjustPublished() throws Exception {
            mockMvc.perform(enter("8", "7", "9").with(as(TestAccounts.TEACHER))).andExpect(status().is3xxRedirection());
            mockMvc.perform(post(MANAGE + "/publish").with(csrf()).with(as(TestAccounts.TEACHER_HEAD)));

            mockMvc.perform(post(SHEET).with(csrf()).param("scores[1_1]", "10").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("vừa được công bố")));
            mockMvc.perform(get(SHEET).with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(not(containsString("scores[1_1]"))));

            Long gradeId = grades().getFirst().getId();
            String adjust = MANAGE + "/" + gradeId + "/adjust";
            mockMvc.perform(get(adjust).with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isOk());
            mockMvc.perform(post(adjust).with(csrf()).param("score", "6").param("reason", "")
                            .with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vui lòng nhập lý do điều chỉnh điểm")));
            mockMvc.perform(post(adjust).with(csrf()).param("score", "6,5").param("reason", "Phúc khảo")
                            .with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(redirectedUrl(MANAGE));

            Grade adjusted = gradeRepository.findById(gradeId).orElseThrow();
            assertThat(adjusted.getScore()).isEqualByComparingTo("6.5");
            assertThat(adjusted.getNote()).isEqualTo("Phúc khảo");
            assertThat(adjusted.isPublished()).isTrue();
            AuditLog audit = lastAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ADJUST_GRADE);
            assertThat(audit.getEntityId()).isEqualTo(gradeId);
            assertThat(audit.getDetail()).contains("student=SE190001", "component=Assignment", "score=8 -> 6.5",
                    "reason=Phúc khảo");
        }

        @Test
        @DisplayName("Giảng viên vào trang điều chỉnh bằng URL → 403")
        void teacherCannotOpenAdjust() throws Exception {
            mockMvc.perform(get(MANAGE + "/39/adjust").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Nhập từ Excel")
    class ExcelImport {

        @Test
        @DisplayName("File mẫu có tiêu đề Mã SV + đầu điểm; file hợp lệ ghi các ô có giá trị")
        void importValidFile() throws Exception {
            mockMvc.perform(get(SHEET + "/import/template").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("mau-nhap-diem.xlsx")));

            byte[] file = ImportFiles.xlsxWithHeader(List.of("Mã SV", "Assignment", "Midterm", "Final"),
                    List.of(ImportFiles.row("SE190001", 8.5, null, "7,5")));
            mockMvc.perform(multipart(SHEET + "/import").file(new MockMultipartFile("file", "diem.xlsx", null, file))
                            .with(csrf()).with(as(TestAccounts.TEACHER)))
                    .andExpect(redirectedUrl(SHEET))
                    .andExpect(flash().attribute("successMessage", "Đã nhập 2 ô điểm từ file Excel"));

            assertThat(grades()).extracting(g -> g.getComponent().getId(), Grade::getScore)
                    .containsExactly(tuple(1L, new BigDecimal("8.50")), tuple(3L, new BigDecimal("7.50")));
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.IMPORT_GRADES);
        }

        @Test
        @DisplayName("File có lỗi: bảng lỗi theo dòng, không ghi ô nào")
        void importInvalidFile() throws Exception {
            byte[] file = ImportFiles.xlsxWithHeader(List.of("Mã SV", "Assignment", "Midterm", "Final"),
                    List.of(ImportFiles.row("SE190001", 8, 11, 9), ImportFiles.row("SE180101", 5, 5, 5)));

            mockMvc.perform(multipart(SHEET + "/import").file(new MockMultipartFile("file", "diem.xlsx", null, file))
                            .with(csrf()).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Điểm phải từ 0 đến 10"),
                            containsString("không đang học lớp học phần này"))));
            assertThat(grades()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("GV lớp khác → 403 khi xem, lưu, nhập Excel")
        void otherTeacherDenied() throws Exception {
            mockMvc.perform(get("/teacher/sections/27/grades").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/teacher/sections/27/grades").with(csrf()).param("scores[17_14]", "1")
                    .with(as(TestAccounts.TEACHER))).andExpect(status().isForbidden());
            mockMvc.perform(get("/teacher/sections/27/grades/import/template").with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
            assertThat(gradeRepository.findById(41L).orElseThrow().getScore()).isEqualByComparingTo("8");
        }

        @Test
        @DisplayName("TEACHER không công bố được (403)")
        void teacherCannotPublish() throws Exception {
            mockMvc.perform(post(MANAGE + "/publish").with(csrf()).with(as(TestAccounts.TEACHER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TBM xem lớp thuộc bộ môn khác → 403")
        void headOtherDepartment() throws Exception {
            Long ia = iaSection();

            mockMvc.perform(get("/admin/sections/" + ia + "/grades").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/sections/" + ia + "/grades/publish").with(csrf())
                    .with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SV và PH gõ URL bảng điểm của giảng viên hoặc quản lý → 403; PH con chưa liên kết → 403")
        void studentParentCannotSeeUnpublished() throws Exception {
            mockMvc.perform(get(SHEET).with(as(TestAccounts.STUDENT))).andExpect(status().isForbidden());
            mockMvc.perform(get(MANAGE).with(as(TestAccounts.STUDENT))).andExpect(status().isForbidden());
            mockMvc.perform(get(SHEET).with(as(TestAccounts.PARENT))).andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/children/2/grades").with(as(TestAccounts.PARENT)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/parent/grades").with(as(TestAccounts.PARENT)))
                    .andExpect(redirectedUrl("/parent/children/1/grades"));
        }

        @Test
        @DisplayName("Chưa đăng nhập → /login")
        void anonymous() throws Exception {
            for (String url : List.of(SHEET, MANAGE, "/student/grades", "/parent/children/1/grades")) {
                mockMvc.perform(get(url)).andExpect(redirectedUrl("/login"));
            }
        }
    }

    private MockHttpServletRequestBuilder enter(String assignment, String midterm, String fin) {
        return post(SHEET).with(csrf()).param("scores[1_1]", assignment).param("scores[1_2]", midterm)
                .param("scores[1_3]", fin);
    }

    /** Điểm của SE190001 trong lớp 1, theo đầu điểm. */
    private List<Grade> grades() {
        return gradeRepository.findBySectionId(1L).stream()
                .sorted(Comparator.comparing(g -> g.getComponent().getId())).toList();
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
        return auditLogRepository.findAll().stream().max(Comparator.comparing(AuditLog::getId)).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
