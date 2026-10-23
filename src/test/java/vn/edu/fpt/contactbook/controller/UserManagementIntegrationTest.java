package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.ParentStudentId;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.ConstraintViolationMapper;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.TestAccounts.PASSWORD;

@IntegrationTest
class UserManagementIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired ParentRepository parentRepository;
    @Autowired ParentStudentRepository parentStudentRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginAttemptService loginAttemptService;

    @AfterEach
    void clearLoginAttempts() {
        for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.STUDENT, "0911000111")) {
            loginAttemptService.reset(phone);
        }
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @ParameterizedTest
        @ValueSource(strings = {"/admin/users", "/admin/users/new", "/admin/users/4", "/admin/users/4/edit",
                "/admin/users/4/roles", "/admin/users/4/parents", "/admin/users/5/children"})
        void adminOpensEveryPage(String path) throws Exception {
            mockMvc.perform(get(path).with(as(TestAccounts.ADMIN))).andExpect(status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
        void otherRolesAreForbidden(String phone) throws Exception {
            mockMvc.perform(get("/admin/users").with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/users/3").with(as(phone))).andExpect(status().isForbidden());
        }

        @Test
        void anonymousGoesToLogin() throws Exception {
            mockMvc.perform(get("/admin/users")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/users/3")).andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn SE chỉ thấy giáo viên tổ chuyên môn SE, kể cả khi sửa tham số lọc")
        void headSeesOnlyOwnDepartmentTeachers() throws Exception {
            mockMvc.perform(get("/admin/users").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Giáo viên tổ chuyên môn"),
                            containsString("GV001"), containsString("GV002"),
                            containsString("GV003"), containsString("GV004"),
                            not(containsString(TestAccounts.STUDENT)),
                            not(containsString(TestAccounts.ADMIN)),
                            not(containsString(TestAccounts.PARENT)),
                            not(containsString("/admin/users/new")))));

            mockMvc.perform(get("/admin/users").param("role", "STUDENT").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(not(containsString(TestAccounts.STUDENT))));
            mockMvc.perform(get("/admin/users").param("departmentId", "38").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(allOf(not(containsString("GV001")),
                            containsString("Không có người dùng phù hợp"))));
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn xem giáo viên ngoài tổ chuyên môn, học sinh hoặc trang thao tác → 403")
        void headIsForbiddenOutsideScope() throws Exception {
            Long iaTeacherUserId = createTeacher("0911999888", "GV-IA-01", 38L);

            mockMvc.perform(get("/admin/users/3").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/admin/users/3/edit"))));
            mockMvc.perform(get("/admin/users/" + iaTeacherUserId).with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/users/4").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/users/new").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/users/3/edit").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/users/3/lock").with(as(TestAccounts.TEACHER_HEAD)).with(csrf()))
                    .andExpect(status().isForbidden());
            assertThat(userRepository.findById(3L).orElseThrow().isActive()).isTrue();
        }

        @Test
        @DisplayName("Menu: ADMIN có 'Người dùng', HEAD có 'Giáo viên tổ chuyên môn'")
        void menuItems() throws Exception {
            mockMvc.perform(get("/admin").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("Người dùng"),
                            not(containsString("Giáo viên tổ chuyên môn")))));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(containsString("Giáo viên tổ chuyên môn")));
            mockMvc.perform(get("/teacher").with(as(TestAccounts.TEACHER)))
                    .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
        }

        @Test
        @DisplayName("Danh sách, chi tiết người dùng và hồ sơ ghi 'Vai trò', không phải 'Vai trò của bạn'")
        void roleHeadingIsNotPersonal() throws Exception {
            for (String path : List.of("/admin/users", "/admin/users/4", "/profile")) {
                mockMvc.perform(get(path).with(as(TestAccounts.ADMIN)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(allOf(containsString(">Vai trò<"),
                                not(containsString("Vai trò của bạn")))));
            }
        }
    }

    @Nested
    @DisplayName("Thêm người dùng")
    class Create {

        @Test
        @DisplayName("TEACHER + HEAD_OF_DEPARTMENT (SE): mật khẩu tạm hiện một lần, đăng nhập bị ép đổi mật khẩu")
        void createTeacherHeadAndLoginWithTemporaryPassword() throws Exception {
            long auditBefore = auditLogRepository.count();
            MvcResult created = mockMvc.perform(newTeacherHead("+84 911 000 111", "gv900", null))
                    .andExpect(redirectedUrlPattern("/admin/users/*"))
                    .andExpect(flash().attributeExists("temporaryPassword"))
                    .andReturn();
            String temporaryPassword = (String) created.getFlashMap().get("temporaryPassword");
            String detailUrl = created.getResponse().getRedirectedUrl();

            mockMvc.perform(get(detailUrl).with(as(TestAccounts.ADMIN)).flashAttrs(created.getFlashMap()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(content().string(allOf(containsString(temporaryPassword),
                            containsString("Tổ trưởng chuyên môn"), containsString("Giáo viên"),
                            containsString("Tổ Toán – Tin học (TT)"), containsString("GV900"))));
            mockMvc.perform(get(detailUrl).with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(not(containsString(temporaryPassword))));

            User user = userRepository.findByPhone("0911000111").orElseThrow();
            assertThat(user.isMustChangePassword()).isTrue();
            assertThat(userRoleRepository.findByUserId(user.getId()))
                    .extracting(ur -> ur.getRole().getCode() + "@" + ur.getDepartment().getCode())
                    .containsExactlyInAnyOrder("HEAD_OF_DEPARTMENT@TT", "TEACHER@TT");
            assertThat(auditLogRepository.count()).isEqualTo(auditBefore + 1);
            assertNoAuditContains(temporaryPassword);

            MockHttpSession session = login("0911000111", temporaryPassword, "/teacher");
            mockMvc.perform(get("/teacher").session(session)).andExpect(redirectedUrl("/change-password"));
            mockMvc.perform(get("/admin/users").session(session)).andExpect(redirectedUrl("/change-password"));
        }

        @Test
        @DisplayName("Số điện thoại trùng, email trùng, sai định dạng: lỗi cạnh từng ô")
        void duplicateAndInvalidFieldsAreShownNextToInputs() throws Exception {
            mockMvc.perform(newTeacherHead(TestAccounts.TEACHER, "GV901", "HEAD@FPT.edu.vn"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Số điện thoại đã được sử dụng"),
                            containsString("Email đã được sử dụng"),
                            containsString("is-invalid"))));

            mockMvc.perform(newTeacherHead("12345", "GV001", "khong-hop-le"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0"),
                            containsString("Email không hợp lệ"))));

            mockMvc.perform(newTeacherHead("0911000112", "GV001", null))
                    .andExpect(content().string(containsString("Mã giáo viên đã tồn tại")));

            mockMvc.perform(post("/admin/users/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("phone", "0911000113").param("fullName", "HS trùng mã").param("roles", "STUDENT")
                            .param("profile.studentCode", "hs260001").param("profile.majorId", "1")
                            .param("profile.classId", "1").param("profile.enrollmentYear", "2026"))
                    .andExpect(content().string(containsString("Mã học sinh đã tồn tại")));

            mockMvc.perform(post("/admin/users/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("phone", "0911000114").param("fullName", "Thiếu vai trò"))
                    .andExpect(content().string(containsString("Vui lòng chọn ít nhất một vai trò")));
            assertThat(userRepository.findByPhone("0911000112")).isEmpty();
        }

        @Test
        @DisplayName("Nhập sai kiểu số hoặc ngày: thông báo tiếng Việt, không lộ lỗi chuyển kiểu của Java")
        void typeMismatchShowsVietnameseMessage() throws Exception {
            mockMvc.perform(post("/admin/users/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("phone", "0911000116").param("fullName", "Sai kiểu").param("roles", "STUDENT")
                            .param("profile.studentCode", "SE998").param("profile.majorId", "1")
                            .param("profile.enrollmentYear", "99999").param("dateOfBirth", "31-12-2000"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Vui lòng nhập số hợp lệ"),
                            containsString("Ngày không hợp lệ"),
                            not(containsString("Failed to convert")),
                            not(containsString("java.lang")))));
            assertThat(userRepository.findByPhone("0911000116")).isEmpty();
        }

        @Test
        @DisplayName("Lớp khác khối của học sinh bị từ chối")
        void classMustMatchMajor() throws Exception {
            mockMvc.perform(post("/admin/users/new").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("phone", "0911000115").param("fullName", "HS sai lớp").param("roles", "STUDENT")
                            .param("profile.studentCode", "SE999").param("profile.majorId", "14")
                            .param("profile.classId", "1").param("profile.enrollmentYear", "2026"))
                    .andExpect(content().string(containsString("Lớp không thuộc khối đã chọn")));
        }

        @Test
        @DisplayName("Lỗi UNIQUE thật từ SQL Server (thao tác đồng thời) đổi được thành lỗi tiếng Việt")
        void realSqlServerUniqueViolationIsMapped() {
            User duplicate = new User();
            duplicate.setPhone(TestAccounts.ADMIN);
            duplicate.setFullName("Trùng");
            duplicate.setPasswordHash("x");

            assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .satisfies(e -> {
                        RuntimeException mapped = ConstraintViolationMapper.map((DataIntegrityViolationException) e);
                        assertThat(mapped).isInstanceOf(BusinessException.class)
                                .hasMessage("Số điện thoại đã được sử dụng");
                    });
        }
    }

    @Nested
    @DisplayName("Sửa, khóa, đặt lại mật khẩu")
    class Manage {

        @Test
        @DisplayName("Sửa số điện thoại (chuẩn hóa) và hồ sơ giáo viên")
        void editUser() throws Exception {
            mockMvc.perform(post("/admin/users/3/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("phone", "+84 911 222 333").param("fullName", "Trần Thị Giáo")
                            .param("profile.teacherCode", "GV002").param("profile.teacherDepartmentId", "1")
                            .param("profile.academicTitle", "TS."))
                    .andExpect(redirectedUrl("/admin/users/3"));

            assertThat(userRepository.findById(3L).orElseThrow().getPhone()).isEqualTo("0911222333");
            assertThat(teacherRepository.findByUserId(3L).orElseThrow().getAcademicTitle()).isEqualTo("TS.");
            assertThat(audit(AuditActions.UPDATE_USER)).isNotEmpty();
        }

        @Test
        @DisplayName("Sửa chính mình: thanh trên hiện tên mới ngay (principal được làm mới)")
        void editSelfRefreshesPrincipal() throws Exception {
            MockHttpSession session = login(TestAccounts.ADMIN, PASSWORD, "/admin");
            mockMvc.perform(post("/admin/users/1/edit").session(session).with(csrf())
                            .param("phone", TestAccounts.ADMIN).param("fullName", "Quản trị Đã Đổi Tên"))
                    .andExpect(redirectedUrl("/admin/users/1"));

            mockMvc.perform(get("/admin").session(session))
                    .andExpect(content().string(containsString("Quản trị Đã Đổi Tên")));
        }

        @Test
        @DisplayName("Khóa thì không đăng nhập được, mở khóa thì đăng nhập lại được")
        void lockAndUnlock() throws Exception {
            mockMvc.perform(post("/admin/users/3/lock").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/users/3"))
                    .andExpect(flash().attribute("successMessage", "Đã khóa tài khoản"));
            mockMvc.perform(formLogin("/login").userParameter("phone").user(TestAccounts.TEACHER).password(PASSWORD))
                    .andExpect(redirectedUrl("/login?error=disabled"));

            mockMvc.perform(post("/admin/users/3/unlock").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/users/3"));
            login(TestAccounts.TEACHER, PASSWORD, "/teacher");

            assertThat(audit(AuditActions.LOCK_USER)).isNotEmpty();
            assertThat(audit(AuditActions.UNLOCK_USER)).isNotEmpty();
        }

        @Test
        @DisplayName("Không tự khóa mình, không tự gỡ ADMIN của mình")
        void selfProtection() throws Exception {
            mockMvc.perform(post("/admin/users/1/lock").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Không thể tự khóa tài khoản của chính mình"));
            Long adminRoleId = userRoleRepository.findByUserId(1L).getFirst().getId();
            mockMvc.perform(post("/admin/users/1/roles/" + adminRoleId + "/remove").with(as(TestAccounts.ADMIN))
                            .with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Không thể tự gỡ vai trò quản trị viên của chính mình"));

            assertThat(userRepository.findById(1L).orElseThrow().isActive()).isTrue();
            assertThat(userRoleRepository.existsByUserIdAndRoleCode(1L, "ADMIN")).isTrue();
        }

        @Test
        @DisplayName("Không khóa quản trị viên đang hoạt động cuối cùng")
        void lastActiveAdmin() throws Exception {
            // Dữ liệu mẫu có 2 ADMIN (user 1 và 0901234567). Khóa user 1 trong DB để 0901234567 là ADMIN
            // đang hoạt động cuối cùng, rồi để user 1 (principal vẫn còn trong phiên) thử khóa người đó.
            User secondAdmin = userRepository.findByPhone("0901234567").orElseThrow();
            User first = userRepository.findById(1L).orElseThrow();
            first.setActive(false);
            userRepository.saveAndFlush(first);

            mockMvc.perform(post("/admin/users/" + secondAdmin.getId() + "/lock").with(as(TestAccounts.ADMIN))
                            .with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Không thể khóa quản trị viên đang hoạt động cuối cùng"));
            assertThat(userRepository.findById(secondAdmin.getId()).orElseThrow().isActive()).isTrue();
        }

        @Test
        @DisplayName("Đặt lại mật khẩu: mật khẩu cũ hết dùng được, mật khẩu tạm bị ép đổi")
        void resetPassword() throws Exception {
            MvcResult reset = mockMvc.perform(post("/admin/users/4/reset-password").with(as(TestAccounts.ADMIN))
                            .with(csrf()))
                    .andExpect(redirectedUrl("/admin/users/4"))
                    .andReturn();
            String temporaryPassword = (String) reset.getFlashMap().get("temporaryPassword");

            mockMvc.perform(formLogin("/login").userParameter("phone").user(TestAccounts.STUDENT).password(PASSWORD))
                    .andExpect(redirectedUrl("/login?error"));
            MockHttpSession session = login(TestAccounts.STUDENT, temporaryPassword, "/student");
            mockMvc.perform(get("/student").session(session)).andExpect(redirectedUrl("/change-password"));

            List<AuditLog> resets = audit(AuditActions.RESET_PASSWORD);
            assertThat(resets).anySatisfy(entry -> {
                assertThat(entry.getEntityId()).isEqualTo(4L);
                assertThat(entry.getUser().getId()).isEqualTo(1L);
            });
            assertNoAuditContains(temporaryPassword);
        }

        @Test
        @DisplayName("POST thao tác thiếu CSRF bị 403")
        void csrfRequired() throws Exception {
            mockMvc.perform(post("/admin/users/3/lock").with(as(TestAccounts.ADMIN)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Vai trò và liên kết phụ huynh")
    class RolesAndLinks {

        @Test
        @DisplayName("Gán vai trò thiếu hồ sơ thì tạo hồ sơ cùng lúc; gỡ vai trò giữ hồ sơ")
        void assignAndRemoveRoleKeepsProfile() throws Exception {
            mockMvc.perform(post("/admin/users/3/roles").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("roleCode", "PARENT").param("profile.occupation", "Giáo viên"))
                    .andExpect(redirectedUrl("/admin/users/3/roles"));
            assertThat(parentRepository.findByUserId(3L)).isPresent();

            mockMvc.perform(post("/admin/users/3/roles").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("roleCode", "PARENT"))
                    .andExpect(content().string(containsString("Người dùng đã có vai trò này với tổ chuyên môn này")));

            mockMvc.perform(post("/admin/users/3/roles").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("roleCode", "STUDENT"))
                    .andExpect(content().string(containsString("Vui lòng nhập mã học sinh")));

            Long parentRoleId = userRoleRepository.findByUserId(3L).stream()
                    .filter(ur -> ur.getRole().getCode().equals("PARENT")).findFirst().orElseThrow().getId();
            mockMvc.perform(post("/admin/users/3/roles/" + parentRoleId + "/remove").with(as(TestAccounts.ADMIN))
                            .with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã gỡ vai trò"));
            assertThat(userRoleRepository.existsByUserIdAndRoleCode(3L, "PARENT")).isFalse();
            assertThat(parentRepository.findByUserId(3L)).as("hồ sơ phụ huynh vẫn còn").isPresent();
            assertThat(audit(AuditActions.ASSIGN_ROLE)).isNotEmpty();
            assertThat(audit(AuditActions.REMOVE_ROLE)).isNotEmpty();
        }

        @Test
        @DisplayName("Thêm phụ huynh chính mới bỏ cờ chính của phụ huynh cũ; gỡ liên kết")
        void linkParentsWithSinglePrimary() throws Exception {
            // HS260001 (students.id = 1) đang có phụ huynh chính là parents.id = 1 (0900000005).
            mockMvc.perform(post("/admin/users/4/parents").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("otherKey", "0944 112 233").param("relationship", "GUARDIAN")
                            .param("primary", "true"))
                    .andExpect(redirectedUrl("/admin/users/4/parents"));

            assertThat(parentStudentRepository.findById(new ParentStudentId(1L, 1L)).orElseThrow().isPrimary()).isFalse();
            assertThat(parentStudentRepository.findById(new ParentStudentId(5L, 1L)).orElseThrow().isPrimary()).isTrue();

            mockMvc.perform(post("/admin/users/4/parents").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("otherKey", "0944112233"))
                    .andExpect(content().string(containsString("Phụ huynh và học sinh này đã được liên kết")));
            mockMvc.perform(post("/admin/users/4/parents").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("otherKey", TestAccounts.TEACHER))
                    .andExpect(content().string(containsString("Không tìm thấy phụ huynh có số điện thoại này")));

            mockMvc.perform(post("/admin/users/4/parents/5/remove").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/users/4/parents"));
            assertThat(parentStudentRepository.existsById(new ParentStudentId(5L, 1L))).isFalse();
            assertThat(audit(AuditActions.LINK_PARENT)).isNotEmpty();
            assertThat(audit(AuditActions.UNLINK_PARENT)).isNotEmpty();
        }

        @Test
        @DisplayName("Từ trang phụ huynh: thêm con theo mã học sinh")
        void linkChildByStudentCode() throws Exception {
            mockMvc.perform(post("/admin/users/5/children").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("otherKey", "hs250101").param("relationship", "FATHER"))
                    .andExpect(redirectedUrl("/admin/users/5/children"));

            assertThat(parentStudentRepository.existsById(new ParentStudentId(1L, 2L))).isTrue();
            mockMvc.perform(get("/admin/users/5").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(containsString("HS250101")));
        }

        @Test
        @DisplayName("Người chưa có hồ sơ học sinh không vào được trang phụ huynh")
        void linksNeedProfile() throws Exception {
            mockMvc.perform(get("/admin/users/3/parents").with(as(TestAccounts.ADMIN)))
                    .andExpect(redirectedUrl("/admin/users/3"))
                    .andExpect(flash().attribute("errorMessage", "Người dùng này chưa có hồ sơ học sinh"));
        }
    }

    @Test
    @DisplayName("Phân trang giữ giá trị lọc")
    void paginationKeepsFilters() throws Exception {
        for (int i = 0; i < 25; i++) {
            User user = new User();
            user.setPhone(String.format("09770000%02d", i));
            user.setFullName("Người Phân Trang " + i);
            user.setPasswordHash("x");
            userRepository.save(user);
        }
        userRepository.flush();

        mockMvc.perform(get("/admin/users").param("keyword", "Phân Trang").param("status", "ACTIVE")
                        .with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(allOf(
                        containsString("25 người dùng"),
                        containsString("page=1&amp;keyword=Ph%C3%A2n%20Trang"),
                        containsString("status=ACTIVE"))));
    }

    @Test
    @DisplayName("Thanh phân trang chỉ hiện trang hiện tại ±2")
    void paginationShowsWindowOfPages() throws Exception {
        for (int i = 0; i < 130; i++) {
            User user = new User();
            user.setPhone(String.format("0978000%03d", i));
            user.setFullName("Người Cửa Sổ " + i);
            user.setPasswordHash("x");
            userRepository.save(user);
        }
        userRepository.flush();

        String firstPage = mockMvc.perform(get("/admin/users").param("keyword", "Cửa Sổ").with(as(TestAccounts.ADMIN)))
                .andReturn().getResponse().getContentAsString();
        assertThat(firstPage).contains("page=2&amp;").doesNotContain("page=3&amp;").doesNotContain("page=6&amp;");

        String middlePage = mockMvc.perform(get("/admin/users").param("keyword", "Cửa Sổ").param("page", "4")
                        .with(as(TestAccounts.ADMIN)))
                .andReturn().getResponse().getContentAsString();
        assertThat(middlePage).contains("page=2&amp;", "page=6&amp;").doesNotContain("page=1&amp;");
    }

    private MockHttpServletRequestBuilder newTeacherHead(String phone, String teacherCode, String email) {
        MockHttpServletRequestBuilder request = post("/admin/users/new").with(as(TestAccounts.ADMIN)).with(csrf())
                .param("phone", phone).param("fullName", "Giáo viên Mới")
                .param("roles", "TEACHER", "HEAD_OF_DEPARTMENT").param("headDepartmentId", "1")
                .param("profile.teacherCode", teacherCode).param("profile.teacherDepartmentId", "1");
        return email == null ? request : request.param("email", email);
    }

    private Long createTeacher(String phone, String code, Long departmentId) {
        User user = new User();
        user.setPhone(phone);
        user.setFullName("GV ngoài tổ chuyên môn");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);
        Department department = departmentRepository.findById(departmentId).orElseThrow();
        Teacher teacher = new Teacher();
        teacher.setUser(user);
        teacher.setTeacherCode(code);
        teacher.setDepartment(department);
        teacherRepository.saveAndFlush(teacher);
        return user.getId();
    }

    private MockHttpSession login(String phone, String password, String expectedHome) throws Exception {
        ResultActions result = mockMvc.perform(formLogin("/login").userParameter("phone").user(phone).password(password))
                .andExpect(redirectedUrl(expectedHome));
        return (MockHttpSession) result.andReturn().getRequest().getSession(false);
    }

    private List<AuditLog> audit(String action) {
        return auditLogRepository.findAll().stream().filter(entry -> entry.getAction().equals(action)).toList();
    }

    private void assertNoAuditContains(String secret) {
        assertThat(auditLogRepository.findAll())
                .noneMatch(entry -> entry.getDetail() != null && entry.getDetail().contains(secret));
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
