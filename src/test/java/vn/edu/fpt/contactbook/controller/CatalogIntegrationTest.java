package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.FacultyRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.RoomRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.ConstraintViolationMapper;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Danh mục chỉ dành cho ADMIN (khoa, bộ môn, ngành, lớp, học kỳ, phòng) trên FptContactBook_test.
 */
@IntegrationTest
class CatalogIntegrationTest {

    static final List<String> ADMIN_ONLY_LISTS = List.of("/admin/catalog/faculties", "/admin/catalog/departments",
            "/admin/catalog/majors", "/admin/catalog/classes", "/admin/catalog/semesters", "/admin/catalog/rooms");

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired FacultyRepository facultyRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired MajorRepository majorRepository;
    @Autowired SchoolClassRepository schoolClassRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @ParameterizedTest
        @ValueSource(strings = {
                "/admin/catalog/faculties", "/admin/catalog/faculties/new", "/admin/catalog/faculties/1",
                "/admin/catalog/faculties/1/edit",
                "/admin/catalog/departments", "/admin/catalog/departments/new", "/admin/catalog/departments/1",
                "/admin/catalog/departments/1/edit",
                "/admin/catalog/majors", "/admin/catalog/majors/new", "/admin/catalog/majors/1",
                "/admin/catalog/majors/1/edit",
                "/admin/catalog/classes", "/admin/catalog/classes/new", "/admin/catalog/classes/1",
                "/admin/catalog/classes/1/edit",
                "/admin/catalog/semesters", "/admin/catalog/semesters/new", "/admin/catalog/semesters/1",
                "/admin/catalog/semesters/1/edit",
                "/admin/catalog/rooms", "/admin/catalog/rooms/new", "/admin/catalog/rooms/1",
                "/admin/catalog/rooms/1/edit"})
        void adminOpensEveryPage(String path) throws Exception {
            mockMvc.perform(get(path).with(as(TestAccounts.ADMIN))).andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {TestAccounts.TEACHER_HEAD, TestAccounts.TEACHER, TestAccounts.STUDENT,
                TestAccounts.PARENT})
        @DisplayName("Tổ trưởng chuyên môn, giáo viên, học sinh, phụ huynh vào danh mục của ADMIN → 403")
        void otherRolesAreForbidden(String phone) throws Exception {
            for (String path : ADMIN_ONLY_LISTS) {
                mockMvc.perform(get(path).with(as(phone))).andExpect(status().isForbidden());
                mockMvc.perform(get(path + "/1").with(as(phone))).andExpect(status().isForbidden());
                mockMvc.perform(post(path + "/new").with(as(phone)).with(csrf()).param("code", "X")
                        .param("name", "X")).andExpect(status().isForbidden());
            }
            mockMvc.perform(post("/admin/catalog/semesters/38/set-current").with(as(phone)).with(csrf()))
                    .andExpect(status().isForbidden());
            assertThat(semesterRepository.findByCurrentTrue()).get().extracting(Semester::getCode).isEqualTo("HK1-2627");
        }

        @Test
        void anonymousGoesToLogin() throws Exception {
            for (String path : ADMIN_ONLY_LISTS) {
                mockMvc.perform(get(path)).andExpect(redirectedUrl("/login"));
            }
        }

        @Test
        @DisplayName("Menu: ADMIN thấy nhóm Danh mục; tổ trưởng chuyên môn chỉ thấy Môn học")
        void menuByRole() throws Exception {
            mockMvc.perform(get("/admin/catalog/faculties").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("Danh mục"),
                            containsString("href=\"/admin/catalog/semesters\""),
                            containsString("href=\"/admin/subjects\""))));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(allOf(containsString("href=\"/admin/subjects\""),
                            not(containsString("href=\"/admin/catalog/faculties\"")))));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(not(containsString("href=\"/admin/subjects\""))));
        }
    }

    @Nested
    @DisplayName("Thêm, sửa")
    class Manage {

        @Test
        @DisplayName("Tạo cấp học: mã chuẩn hóa, ghi audit với entity_type Faculty")
        void createFaculty() throws Exception {
            mockMvc.perform(post("/admin/catalog/faculties/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("code", "  law ").param("name", "Cấp học Luật"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/faculties/*"))
                    .andExpect(flash().attribute("successMessage", "Đã thêm cấp học"));

            Faculty created = facultyRepository.findAll().stream().filter(f -> "LAW".equals(f.getCode()))
                    .findFirst().orElseThrow();
            assertThat(created.getName()).isEqualTo("Cấp học Luật");
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_FACULTY);
            assertThat(lastAudit().getEntityType()).isEqualTo("Faculty");
            assertThat(lastAudit().getEntityId()).isEqualTo(created.getId());
        }

        @Test
        @DisplayName("Mã trùng (khác hoa thường, có khoảng trắng) → lỗi cạnh ô mã, không tạo")
        void duplicateCodeShowsFieldError() throws Exception {
            long before = departmentRepository.count();

            mockMvc.perform(post("/admin/catalog/departments/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("facultyId", "1").param("code", " tt ").param("name", "Trùng"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Mã tổ chuyên môn đã tồn tại"),
                            containsString("is-invalid"))));
            assertThat(departmentRepository.count()).isEqualTo(before);
        }

        @Test
        void requiredFieldsShowVietnameseErrors() throws Exception {
            mockMvc.perform(post("/admin/catalog/majors/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("code", "").param("name", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Vui lòng chọn cấp học"),
                            containsString("Vui lòng nhập mã"), containsString("Vui lòng nhập tên"))));
        }

        @Test
        @DisplayName("Tạo tổ chuyên môn, khối, lớp, học kỳ, phòng trên form thật")
        void createEachCatalog() throws Exception {
            mockMvc.perform(post("/admin/catalog/departments/new").with(as(TestAccounts.ADMIN)).with(csrf())
                    .param("facultyId", "1").param("code", "ds").param("name", "Tổ chuyên môn Khoa học dữ liệu"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/departments/*"));
            mockMvc.perform(post("/admin/catalog/majors/new").with(as(TestAccounts.ADMIN)).with(csrf())
                    .param("facultyId", "1").param("code", "ds").param("name", "Khoa học dữ liệu"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/majors/*"));
            mockMvc.perform(post("/admin/catalog/classes/new").with(as(TestAccounts.ADMIN)).with(csrf())
                    .param("majorId", "1").param("code", "se2001").param("name", "Lớp SE2001")
                    .param("intakeYear", "2024").param("homeroomTeacherCode", "gv002"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/classes/*"));
            mockMvc.perform(post("/admin/catalog/semesters/new").with(as(TestAccounts.ADMIN)).with(csrf())
                    .param("code", "su27").param("name", "Summer 2027")
                    .param("startDate", "2027-05-03").param("endDate", "2027-08-22"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/semesters/*"));
            mockMvc.perform(post("/admin/catalog/rooms/new").with(as(TestAccounts.ADMIN)).with(csrf())
                    .param("code", "ga-101").param("name", "Phòng Gamma 101").param("building", "Tòa Gamma"))
                    .andExpect(redirectedUrlPattern("/admin/catalog/rooms/*"));

            assertThat(departmentRepository.existsByCode("DS")).isTrue();
            assertThat(majorRepository.existsByCode("DS")).isTrue();
            assertThat(schoolClassRepository.findAll()).filteredOn(c -> "SE2001".equals(c.getCode()))
                    .singleElement().satisfies(c -> assertThat(c.getHomeroomTeacher().getTeacherCode())
                            .isEqualTo("GV002"));
            assertThat(semesterRepository.findAll()).filteredOn(s -> "SU27".equals(s.getCode()))
                    .singleElement().satisfies(s -> assertThat(s.isCurrent()).isFalse());
            assertThat(roomRepository.findAll()).filteredOn(r -> "GA-101".equals(r.getCode()))
                    .singleElement().satisfies(r -> assertThat(r.getCapacity()).isNull());
            mockMvc.perform(get("/admin/catalog/rooms").param("keyword", "gamma").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("GA-101"), containsString("LAB-401"),
                            not(containsString("R101")))));
        }

        @Test
        @DisplayName("Lớp 10A1 đã có học sinh: đổi khối bị chặn, lỗi cạnh ô khối")
        void classWithStudentsKeepsMajor() throws Exception {
            mockMvc.perform(post("/admin/catalog/classes/1/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("majorId", "14").param("code", "10A1").param("name", "Lớp 10A1")
                            .param("intakeYear", "2019"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Lớp đã có học sinh nên không đổi được khối")));
            assertThat(schoolClassRepository.findById(1L)).get()
                    .extracting(c -> c.getMajor().getId()).isEqualTo(1L);
        }

        @Test
        void semesterDatesValidated() throws Exception {
            mockMvc.perform(post("/admin/catalog/semesters/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("code", "BAD1").param("name", "Sai ngày")
                            .param("startDate", "2027-05-03").param("endDate", "2027-05-03"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ngày kết thúc phải sau ngày bắt đầu")));
            assertThat(semesterRepository.existsByCode("BAD1")).isFalse();
        }

        @Test
        void roomCapacityValidated() throws Exception {
            mockMvc.perform(post("/admin/catalog/rooms/1/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("code", "R101").param("name", "Phòng 101").param("capacity", "0"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sức chứa phải từ 1 đến 32767")));
            mockMvc.perform(post("/admin/catalog/rooms/1/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("code", "R101").param("name", "Phòng 101").param("capacity", "abc"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Vui lòng nhập số nguyên hợp lệ")));
            assertThat(roomRepository.findById(1L)).get().extracting(Room::getCapacity).isEqualTo((short) 45);
        }
    }

    @Nested
    @DisplayName("Học kỳ hiện tại")
    class CurrentSemester {

        @Test
        @DisplayName("Đặt HK2-2627 làm hiện tại: HK1-2627 tự bỏ cờ, luôn đúng một học kỳ hiện tại, ghi audit")
        void setCurrentKeepsExactlyOne() throws Exception {
            mockMvc.perform(post("/admin/catalog/semesters/38/set-current").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/catalog/semesters"))
                    .andExpect(flash().attribute("successMessage", "Đã đặt học kỳ hiện tại"));

            assertThat(semesterRepository.findAll()).filteredOn(Semester::isCurrent)
                    .extracting(Semester::getCode).containsExactly("HK2-2627");
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.SET_CURRENT_SEMESTER);
            assertThat(lastAudit().getEntityType()).isEqualTo("Semester");

            mockMvc.perform(post("/admin/catalog/semesters/1/set-current").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/catalog/semesters"));
            assertThat(semesterRepository.findAll()).filteredOn(Semester::isCurrent)
                    .extracting(Semester::getCode).containsExactly("HK1-2627");
        }

        @Test
        void listShowsCurrentBadgeAndButtons() throws Exception {
            mockMvc.perform(get("/admin/catalog/semesters").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("Hiện tại"),
                            containsString("/admin/catalog/semesters/38/set-current"),
                            not(containsString("/admin/catalog/semesters/1/set-current")),
                            containsString("07/09/2026"))));
        }

        @Test
        void unknownSemesterIsNotFound() throws Exception {
            mockMvc.perform(get("/admin/catalog/semesters/999999").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Lỗi ràng buộc thật từ SQL Server")
    class DatabaseConstraints {

        @Test
        @DisplayName("Trùng mã lọt qua kiểm tra trước (uq_faculties_code) → lỗi ô mã")
        void uniqueCodeFromDatabase() {
            Faculty duplicate = new Faculty();
            duplicate.setCode("THPT");
            duplicate.setName("Trùng");

            assertThatThrownBy(() -> facultyRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintViolationMapper.map((DataIntegrityViolationException) e))
                            .isInstanceOfSatisfying(BusinessException.class, mapped -> {
                                assertThat(mapped).hasMessage("Mã cấp học đã tồn tại");
                                assertThat(mapped.getField()).isEqualTo("code");
                            }));
        }

        @Test
        @DisplayName("Hai học kỳ hiện tại (ux_semesters_current) → thông báo thử lại")
        void secondCurrentSemesterFromDatabase() {
            Semester spring = semesterRepository.findById(38L).orElseThrow();
            spring.setCurrent(true);

            assertThatThrownBy(() -> semesterRepository.saveAndFlush(spring))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintViolationMapper.map((DataIntegrityViolationException) e))
                            .isInstanceOf(BusinessException.class)
                            .hasMessageContaining("Học kỳ hiện tại vừa được người khác thay đổi"));
        }

        @Test
        @DisplayName("CHECK ck_semesters_dates (dấu nháy kép trong thông báo) → lỗi ô ngày kết thúc")
        void checkConstraintFromDatabase() {
            Semester invalid = new Semester();
            invalid.setCode("CK01");
            invalid.setName("Sai ngày");
            invalid.setStartDate(LocalDate.of(2027, 5, 3));
            invalid.setEndDate(LocalDate.of(2027, 5, 1));

            assertThatThrownBy(() -> semesterRepository.saveAndFlush(invalid))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> assertThat(ConstraintViolationMapper.map((DataIntegrityViolationException) e))
                            .isInstanceOfSatisfying(BusinessException.class,
                                    mapped -> assertThat(mapped.getField()).isEqualTo("endDate")));
        }
    }

    private AuditLog lastAudit() {
        return auditLogRepository.findAll().stream()
                .max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
