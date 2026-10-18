package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Môn học và đầu điểm: ADMIN mọi môn, trưởng bộ môn SE (0900000002) chỉ môn bộ môn SE.
 * Dữ liệu mẫu chỉ có môn của bộ môn SE nên mỗi test tạo một môn của bộ môn IA (id 38), rollback sau test.
 */
@IntegrationTest
class SubjectIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired SubjectRepository subjectRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired GradeComponentRepository gradeComponentRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private Long iaSubjectId;
    private Long iaComponentId;

    @BeforeEach
    void createSubjectOfAnotherDepartment() {
        Subject subject = new Subject();
        subject.setDepartment(departmentRepository.findById(38L).orElseThrow());
        subject.setCode("IAO201");
        subject.setName("Nhập môn an toàn thông tin");
        subject.setCredits((short) 3);
        iaSubjectId = subjectRepository.saveAndFlush(subject).getId();

        GradeComponent component = new GradeComponent();
        component.setSubject(subject);
        component.setName("Lab");
        component.setWeight(new BigDecimal("40"));
        iaComponentId = gradeComponentRepository.saveAndFlush(component).getId();
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        void adminOpensEveryPage() throws Exception {
            for (String path : new String[]{"/admin/subjects", "/admin/subjects/new", "/admin/subjects/1",
                    "/admin/subjects/1/edit", "/admin/subjects/1/components/1/edit",
                    "/admin/subjects/" + iaSubjectId}) {
                mockMvc.perform(get(path).with(as(TestAccounts.ADMIN))).andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Trưởng bộ môn SE: thấy và mở được môn bộ môn SE, danh sách không có môn bộ môn khác")
        void headSeesOwnDepartment() throws Exception {
            mockMvc.perform(get("/admin/subjects").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("PRJ301"), containsString("SWP391"),
                            not(containsString("IAO201")))));
            mockMvc.perform(get("/admin/subjects").param("groupId", "38").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(not(containsString("IAO201"))));
            mockMvc.perform(get("/admin/subjects/1").with(as(TestAccounts.TEACHER_HEAD))).andExpect(status().isOk());
            mockMvc.perform(get("/admin/subjects/1/edit").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/admin/subjects/new").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("IA - "))));
        }

        @Test
        @DisplayName("Trưởng bộ môn SE gõ thẳng URL môn bộ môn IA (xem, sửa, đầu điểm) → 403, dữ liệu không đổi")
        void headForbiddenOnOtherDepartmentByUrl() throws Exception {
            String base = "/admin/subjects/" + iaSubjectId;
            RequestPostProcessor head = as(TestAccounts.TEACHER_HEAD);

            mockMvc.perform(get(base).with(head)).andExpect(status().isForbidden());
            mockMvc.perform(get(base + "/edit").with(head)).andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/edit").with(head).with(csrf()).param("departmentId", "38")
                    .param("code", "IAO201").param("name", "Đổi tên").param("credits", "4"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/edit").with(head).with(csrf()).param("code", ""))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(base + "/components/" + iaComponentId + "/edit").with(head))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/components").with(head).with(csrf()).param("name", "Quiz")
                    .param("weight", "10").param("maxScore", "10")).andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/components/" + iaComponentId + "/edit").with(head).with(csrf())
                    .param("name", "Lab 2").param("weight", "50").param("maxScore", "10"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(base + "/components/" + iaComponentId + "/delete").with(head).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/subjects/999999").with(head)).andExpect(status().isForbidden());

            Subject unchanged = subjectRepository.findById(iaSubjectId).orElseThrow();
            assertThat(unchanged.getName()).isEqualTo("Nhập môn an toàn thông tin");
            assertThat(gradeComponentRepository.findBySubjectIdOrderByIdAsc(iaSubjectId))
                    .extracting(GradeComponent::getName).containsExactly("Lab");
        }

        @Test
        @DisplayName("Trưởng bộ môn SE tạo môn hoặc chuyển môn SE sang bộ môn IA → 403")
        void headCannotUseOtherDepartmentInForm() throws Exception {
            mockMvc.perform(post("/admin/subjects/new").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("departmentId", "38").param("code", "IAO999").param("name", "X")
                            .param("credits", "3"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/subjects/1/edit").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("departmentId", "38").param("code", "PRJ301").param("name", "X")
                            .param("credits", "3"))
                    .andExpect(status().isForbidden());
            assertThat(subjectRepository.existsByCode("IAO999")).isFalse();
            assertThat(subjectRepository.findById(1L)).get()
                    .extracting(s -> s.getDepartment().getId()).isEqualTo(1L);
        }

        @ParameterizedTest
        @ValueSource(strings = {TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
        void otherRolesAreForbidden(String phone) throws Exception {
            mockMvc.perform(get("/admin/subjects").with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/subjects/1").with(as(phone))).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/subjects/1/components/1/delete").with(as(phone)).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anonymousGoesToLogin() throws Exception {
            mockMvc.perform(get("/admin/subjects")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/subjects/1")).andExpect(redirectedUrl("/login"));
        }
    }

    @Nested
    @DisplayName("Môn học và đầu điểm")
    class Manage {

        @Test
        @DisplayName("Trưởng bộ môn tạo môn ở bộ môn mình; mã trùng → lỗi cạnh ô")
        void headCreatesSubject() throws Exception {
            mockMvc.perform(post("/admin/subjects/new").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("departmentId", "1").param("code", " swd392 ").param("name", "Kiến trúc phần mềm")
                            .param("credits", "3"))
                    .andExpect(status().is3xxRedirection());
            assertThat(subjectRepository.existsByCode("SWD392")).isTrue();
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_SUBJECT);
            assertThat(lastAudit().getEntityType()).isEqualTo("Subject");

            mockMvc.perform(post("/admin/subjects/new").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("departmentId", "1").param("code", "prj301").param("name", "Trùng")
                            .param("credits", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("Mã môn học đã tồn tại"),
                            containsString("is-invalid"))));
        }

        @Test
        @DisplayName("PRJ301 đã có lớp học phần: không đổi được bộ môn")
        void subjectWithClassSectionsKeepsDepartment() throws Exception {
            mockMvc.perform(post("/admin/subjects/1/edit").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("departmentId", "38").param("code", "PRJ301")
                            .param("name", "Java Web Application Development").param("credits", "3"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Môn học đã có lớp học phần nên không đổi được bộ môn")));
        }

        @Test
        @DisplayName("Trang môn PRJ301: tổng trọng số 100%, không cảnh báo")
        void totalWeightComplete() throws Exception {
            mockMvc.perform(get("/admin/subjects/1").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(allOf(containsString("Assignment"), containsString("Final"),
                            containsString("100,00%"), not(containsString("id=\"weightWarning\"")))));
        }

        @Test
        @DisplayName("Thêm đầu điểm làm tổng khác 100% → hiện cảnh báo; xóa đi → hết cảnh báo; ghi audit")
        void addAndDeleteComponentUpdatesWarning() throws Exception {
            mockMvc.perform(post("/admin/subjects/1/components").with(as(TestAccounts.TEACHER_HEAD)).with(csrf())
                            .param("name", " Quiz ").param("weight", "12.5").param("maxScore", "10"))
                    .andExpect(redirectedUrl("/admin/subjects/1"))
                    .andExpect(flash().attribute("successMessage", "Đã thêm đầu điểm"));
            GradeComponent quiz = gradeComponentRepository.findBySubjectIdOrderByIdAsc(1L).stream()
                    .filter(c -> "Quiz".equals(c.getName())).findFirst().orElseThrow();
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.CREATE_GRADE_COMPONENT);
            assertThat(lastAudit().getEntityType()).isEqualTo("GradeComponent");

            mockMvc.perform(get("/admin/subjects/1").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(allOf(containsString("112,50%"),
                            containsString("id=\"weightWarning\""))));

            mockMvc.perform(post("/admin/subjects/1/components/" + quiz.getId() + "/delete")
                            .with(as(TestAccounts.TEACHER_HEAD)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/subjects/1"))
                    .andExpect(flash().attribute("successMessage", "Đã xóa đầu điểm"));
            assertThat(gradeComponentRepository.findById(quiz.getId())).isEmpty();
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.DELETE_GRADE_COMPONENT);
            mockMvc.perform(get("/admin/subjects/1").with(as(TestAccounts.TEACHER_HEAD)))
                    .andExpect(content().string(not(containsString("id=\"weightWarning\""))));
        }

        @Test
        @DisplayName("Đầu điểm trùng tên, trọng số sai, quá 2 chữ số thập phân → lỗi cạnh ô")
        void componentValidation() throws Exception {
            mockMvc.perform(post("/admin/subjects/1/components").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("name", "assignment").param("weight", "10").param("maxScore", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Môn học đã có đầu điểm cùng tên")));
            mockMvc.perform(post("/admin/subjects/1/components").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("name", "Quiz").param("weight", "0").param("maxScore", "11"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(
                            containsString("Trọng số phải lớn hơn 0 và không quá 100"),
                            containsString("Điểm tối đa phải lớn hơn 0 và không quá 10"))));
            mockMvc.perform(post("/admin/subjects/1/components").with(as(TestAccounts.ADMIN)).with(csrf())
                            .param("name", "Quiz").param("weight", "10.555").param("maxScore", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Trọng số tối đa 2 chữ số thập phân")));
            assertThat(gradeComponentRepository.findBySubjectIdOrderByIdAsc(1L)).hasSize(3);
        }

        @Test
        @DisplayName("Đầu điểm đã có điểm (PRN211 Assignment 1) → không có nút xóa, gửi thẳng thì bị chặn")
        void componentWithGradesCannotBeDeleted() throws Exception {
            mockMvc.perform(get("/admin/subjects/31").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("Đã có điểm"),
                            not(containsString("/admin/subjects/31/components/10/delete")),
                            containsString("/admin/subjects/31/components/11/delete"))));

            mockMvc.perform(post("/admin/subjects/31/components/10/delete").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/subjects/31"))
                    .andExpect(flash().attribute("errorMessage", "Đầu điểm đã có điểm nên không xóa được"));
            assertThat(gradeComponentRepository.findById(10L)).isPresent();
        }

        @Test
        void editComponent() throws Exception {
            mockMvc.perform(post("/admin/subjects/1/components/1/edit").with(as(TestAccounts.TEACHER_HEAD))
                            .with(csrf()).param("name", "Assignment").param("weight", "25").param("maxScore", "9.5"))
                    .andExpect(redirectedUrl("/admin/subjects/1"));
            GradeComponent edited = gradeComponentRepository.findById(1L).orElseThrow();
            assertThat(edited.getWeight()).isEqualByComparingTo("25");
            assertThat(edited.getMaxScore()).isEqualByComparingTo("9.5");
            assertThat(lastAudit().getAction()).isEqualTo(AuditActions.UPDATE_GRADE_COMPONENT);

            mockMvc.perform(get("/admin/subjects/1/components/" + iaComponentId + "/edit")
                    .with(as(TestAccounts.ADMIN))).andExpect(status().isNotFound());
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
