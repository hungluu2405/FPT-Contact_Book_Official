package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.ImportFiles;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

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
 * Lớp học phần và ghi danh trên FptContactBook_test. Lớp mẫu 1 (TOAN10-HK1-2627-01, bộ môn SE) có HS260001.
 * Dữ liệu mẫu chỉ có lớp của bộ môn SE nên mỗi test tạo một lớp của môn bộ môn IA (id 38), rollback sau test.
 */
@IntegrationTest
class ClassSectionIntegrationTest {

    static final long HS260001 = 1L;
    static final long HS250101 = 2L;

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ClassSectionRepository classSectionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private Long iaSectionId;

    @BeforeEach
    void createSectionOfAnotherDepartment() {
        Subject subject = new Subject();
        subject.setDepartment(departmentRepository.findById(38L).orElseThrow());
        subject.setCode("IAO201");
        subject.setName("Nhập môn an toàn thông tin");
        subject.setCredits((short) 3);
        subjectRepository.saveAndFlush(subject);
        iaSectionId = section("IAO201-HK1-2627-01", subject, null).getId();
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        void adminOpensEveryPage() throws Exception {
            for (String path : new String[]{"/admin/sections", "/admin/sections/new", "/admin/sections/1",
                    "/admin/sections/1?q=se&status=ALL", "/admin/sections/1/edit",
                    "/admin/sections/1/enrollments/import", "/admin/sections/" + iaSectionId}) {
                mockMvc.perform(get(path).with(as(TestAccounts.ADMIN))).andExpect(status().isOk());
            }
            mockMvc.perform(get("/admin/sections/1/enrollments/import/template").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("mau-ghi-danh.xlsx")));
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn SE: xem lớp tổ chuyên môn mình (không có khối ghi danh), danh sách không có lớp tổ chuyên môn khác")
        void headSeesOwnSectionsReadOnly() throws Exception {
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);
            mockMvc.perform(get("/admin/sections").with(head))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("TOAN10-HK1-2627-01"),
                            not(containsString("IAO201-HK1-2627-01")))));
            mockMvc.perform(get("/admin/sections/1").with(head))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("HS260001"),
                            not(containsString("/admin/sections/1/enrollments")))));
            mockMvc.perform(get("/admin/sections/1/edit").with(head)).andExpect(status().isOk());
            mockMvc.perform(get("/admin/sections/new").with(head)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn SE gõ thẳng URL lớp tổ chuyên môn IA (xem, sửa) → 403, dữ liệu không đổi")
        void headForbiddenOnOtherDepartment() throws Exception {
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);
            String base = "/admin/sections/" + iaSectionId;
            mockMvc.perform(get(base).with(head)).andExpect(status().isForbidden());
            mockMvc.perform(get(base + "/edit").with(head)).andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/edit").with(head).with(csrf()).param("subjectId", "1")
                    .param("semesterId", "1").param("teacherId", "2").param("code", "HACK").param("status", "OPEN"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/edit").with(head).with(csrf()).param("action", "suggest"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/999999").with(head)).andExpect(status().isForbidden());
            assertThat(classSectionRepository.findById(iaSectionId)).get()
                    .extracting(ClassSection::getCode).isEqualTo("IAO201-HK1-2627-01");
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn POST ghi danh, gỡ, ghi danh theo lớp, nhập Excel → 403")
        void headCannotEnroll() throws Exception {
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);
            mockMvc.perform(post("/admin/sections/1/enrollments").with(head).with(csrf())
                    .param("studentId", String.valueOf(HS250101))).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/sections/1/enrollments/1/drop").with(head).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/sections/1/enrollments/class").with(head).with(csrf()).param("classId", "14"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/1/enrollments/import").with(head)).andExpect(status().isForbidden());
            assertThat(enrollmentRepository.countByClassSectionIdAndStatus(1L, EnrollmentStatus.ENROLLED))
                    .isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(strings = {TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
        void otherRolesAreForbidden(String phone) throws Exception {
            mockMvc.perform(get("/admin/sections").with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/sections/1").with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/sections/1/enrollments").with(as(phone)).with(csrf())
                    .param("studentId", "2")).andExpect(status().isForbidden());
        }

        @Test
        void anonymousGoesToLogin() throws Exception {
            mockMvc.perform(get("/admin/sections")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/sections/1")).andExpect(redirectedUrl("/login"));
        }

        @Test
        void menuShowsSectionsForAdminAndHead() throws Exception {
            mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(containsString("href=\"/admin/sections\"")));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(containsString("href=\"/admin/sections\"")));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(not(containsString("href=\"/admin/sections\""))));
        }
    }

    @Nested
    @DisplayName("Thêm, sửa lớp môn học")
    class Manage {

        @Test
        @DisplayName("Tạo lớp không nhập mã: tự đề xuất TOAN10-HK2-2627-01, mặc định OPEN, ghi audit")
        void createWithSuggestedCode() throws Exception {
            mockMvc.perform(post("/admin/sections/new").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("subjectId", "1").param("semesterId", "38").param("teacherId", "2")
                            .param("maxStudents", "40").param("status", "OPEN"))
                    .andExpect(redirectedUrlPattern("/admin/sections/*"));
            ClassSection created = byCode("TOAN10-HK2-2627-01");
            assertThat(created.getStatus()).isEqualTo(SectionStatus.OPEN);
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_CLASS_SECTION);
            assertThat(lastAudit().getEntityType()).isEqualTo("ClassSection");
        }

        @Test
        void suggestButtonFillsCodeWithoutSaving() throws Exception {
            long before = classSectionRepository.count();
            mockMvc.perform(post("/admin/sections/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("action", "suggest").param("subjectId", "31").param("semesterId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"TIN11-HK1-2627-02\"")));
            assertThat(classSectionRepository.count()).isEqualTo(before);
        }

        @Test
        @DisplayName("Mã trùng → lỗi cạnh ô; giảm sĩ số dưới số HS đang học → lỗi cạnh ô")
        void validationErrorsNextToFields() throws Exception {
            mockMvc.perform(post("/admin/sections/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("subjectId", "1").param("semesterId", "1").param("teacherId", "2")
                            .param("code", " toan10-hk1-2627-01 ").param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Mã lớp môn học đã tồn tại"),
                            containsString("is-invalid"))));

            mockMvc.perform(post("/admin/sections/27/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("subjectId", "31").param("semesterId", "1").param("teacherId", "8")
                            .param("code", "TIN11-HK1-2627-01").param("maxStudents", "1").param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Sĩ số tối đa không được nhỏ hơn số học sinh đang học (2)")));
            mockMvc.perform(post("/admin/sections/27/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("subjectId", "1").param("semesterId", "38").param("teacherId", "8")
                            .param("code", "TIN11-HK1-2627-01").param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Lớp đã có học sinh ghi danh nên không đổi được môn học"),
                            containsString("Lớp đã có học sinh ghi danh nên không đổi được học kỳ"))));
            assertThat(classSectionRepository.findById(27L)).get()
                    .satisfies(s -> assertThat(s.getSubject().getId()).isEqualTo(31L));
        }
    }

    @Nested
    @DisplayName("Ghi danh")
    class Enroll {

        @Test
        @DisplayName("Ghi danh HS260001, trùng bị chặn, vượt sĩ số bị chặn, gỡ rồi ghi danh lại trên cùng dòng")
        void enrollDuplicateFullDropAndReEnroll() throws Exception {
            ClassSection section = section("TOAN10-HK2-2627-09", subjectRepository.findById(1L).orElseThrow(), 1);
            String base = "/admin/sections/" + section.getId();

            enroll(base, HS260001).andExpect(flash().attribute("successMessage", "Đã ghi danh học sinh"));
            Enrollment first = enrollmentRepository.findByClassSectionIdAndStudentId(section.getId(), HS260001)
                    .orElseThrow();
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.ENROLL_STUDENT);

            enroll(base, HS260001).andExpect(flash().attribute("errorMessage", "Học sinh đã có trong lớp môn học"));
            enroll(base, HS250101).andExpect(flash().attribute("errorMessage", "Lớp môn học đã đủ sĩ số (1/1)"));

            mockMvc.perform(post(base + "/enrollments/" + HS260001 + "/drop").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl(base))
                    .andExpect(flash().attribute("successMessage", "Đã gỡ học sinh khỏi lớp"));
            assertThat(enrollmentRepository.findById(first.getId())).get()
                    .extracting(Enrollment::getStatus).isEqualTo(EnrollmentStatus.DROPPED);
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.DROP_STUDENT);

            enroll(base, HS260001).andExpect(flash().attribute("successMessage", "Đã ghi danh lại học sinh"));
            assertThat(enrollmentRepository.findByClassSectionId(section.getId())).singleElement().satisfies(e -> {
                assertThat(e.getId()).isEqualTo(first.getId());
                assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
            });

            mockMvc.perform(get(base).with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("HS260001"), containsString("1 / 1"),
                            containsString("Đã đủ sĩ số"))));
        }

        @Test
        @DisplayName("Lớp đã CLOSED → từ chối ghi danh với thông báo tiếng Việt")
        void closedSectionRejected() throws Exception {
            ClassSection section = section("TOAN10-HK2-2627-08", subjectRepository.findById(1L).orElseThrow(), null);
            section.setStatus(SectionStatus.CLOSED);
            classSectionRepository.saveAndFlush(section);

            enroll("/admin/sections/" + section.getId(), HS260001).andExpect(flash().attribute("errorMessage",
                    "Lớp môn học không ở trạng thái mở nên không ghi danh được"));
            assertThat(enrollmentRepository.findByClassSectionId(section.getId())).isEmpty();
        }

        @Test
        @DisplayName("Ghi danh lớp hành chính 11A1 rồi lặp lại: lần sau bỏ qua HS đã có")
        void enrollAdministrativeClass() throws Exception {
            ClassSection section = section("TOAN10-HK2-2627-07", subjectRepository.findById(1L).orElseThrow(), null);
            String base = "/admin/sections/" + section.getId();

            mockMvc.perform(post(base + "/enrollments/class").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("classId", "14"))
                    .andExpect(flash().attribute("successMessage", "Đã ghi danh 2 học sinh mới, ghi danh lại 0; "
                            + "bỏ qua 0 học sinh đã có trong lớp và 0 học sinh không đang học"));
            mockMvc.perform(post(base + "/enrollments/class").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("classId", "14"))
                    .andExpect(flash().attribute("successMessage", "Đã ghi danh 0 học sinh mới, ghi danh lại 0; "
                            + "bỏ qua 2 học sinh đã có trong lớp và 0 học sinh không đang học"));
            assertThat(enrollmentRepository.countByClassSectionIdAndStatus(section.getId(), EnrollmentStatus.ENROLLED))
                    .isEqualTo(2);
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.ENROLL_CLASS);

            ClassSection small = section("TOAN10-HK2-2627-06", subjectRepository.findById(1L).orElseThrow(), 1);
            mockMvc.perform(post("/admin/sections/" + small.getId() + "/enrollments/class")
                            .with(as(TestAccounts.ADMIN)).with(csrf()).param("classId", "14"))
                    .andExpect(flash().attribute("errorMessage",
                            "Không đủ chỗ: cần thêm 2 học sinh nhưng lớp chỉ còn 1 chỗ"));
            assertThat(enrollmentRepository.findByClassSectionId(small.getId())).isEmpty();
        }

        @Test
        @DisplayName("Nhập Excel: file lỗi hiện bảng lỗi theo dòng, không ghi ai; file đúng ghi danh tất cả")
        void importFromExcel() throws Exception {
            ClassSection section = section("TOAN10-HK2-2627-05", subjectRepository.findById(1L).orElseThrow(), null);
            String url = "/admin/sections/" + section.getId() + "/enrollments/import";

            byte[] bad = ImportFiles.xlsxWithHeader(List.of("Mã HS"),
                    List.of(ImportFiles.row("HS260001"), ImportFiles.row("SE000000")));
            mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "ds.xlsx", null, bad))
                            .with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("File có 1 lỗi"),
                            containsString("Mã học sinh &quot;SE000000&quot; không tồn tại"))));
            assertThat(enrollmentRepository.findByClassSectionId(section.getId())).isEmpty();

            byte[] good = ImportFiles.xlsxWithHeader(List.of("Mã HS"),
                    List.of(ImportFiles.row("hs260001"), ImportFiles.row("HS250101")));
            MvcResult result = mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "ds.xlsx", null, good))
                            .with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/sections/" + section.getId()))
                    .andReturn();
            assertThat(result.getFlashMap().get("successMessage"))
                    .isEqualTo("Đã ghi danh 2 học sinh mới và ghi danh lại 0 học sinh từ file Excel");
            assertThat(enrollmentRepository.countByClassSectionIdAndStatus(section.getId(), EnrollmentStatus.ENROLLED))
                    .isEqualTo(2);
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.IMPORT_ENROLLMENTS);
        }

        @Test
        void candidateSearchShowsOnlyStudentsNotInSection() throws Exception {
            mockMvc.perform(get("/admin/sections/1").param("q", "HS").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("HS250101"),
                            containsString("name=\"studentId\" value=\"" + HS250101 + "\""),
                            not(containsString("name=\"studentId\" value=\"" + HS260001 + "\"")))));
        }
    }

    private ResultActions enroll(String base, long studentId) throws Exception {
        return mockMvc.perform(post(base + "/enrollments").with(as(TestAccounts.ADMIN)).with(csrf())
                        .param("studentId", String.valueOf(studentId)))
                .andExpect(redirectedUrl(base));
    }

    private ClassSection section(String code, Subject subject, Integer maxStudents) {
        ClassSection section = new ClassSection();
        section.setCode(code);
        section.setSubject(subject);
        section.setSemester(semesterRepository.findById(38L).orElseThrow());
        section.setTeacher(teacherRepository.findById(2L).orElseThrow());
        section.setMaxStudents(maxStudents == null ? null : maxStudents.shortValue());
        return classSectionRepository.saveAndFlush(section);
    }

    private ClassSection byCode(String code) {
        return classSectionRepository.findAll().stream().filter(s -> code.equals(s.getCode())).findFirst()
                .orElseThrow();
    }

    private AuditLog lastAudit() {
        return auditLogRepository.findAll().stream()
                .max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
