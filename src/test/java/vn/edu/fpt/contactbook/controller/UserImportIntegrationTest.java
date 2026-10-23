package vn.edu.fpt.contactbook.controller;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.service.ImportColumn;
import vn.edu.fpt.contactbook.service.UserImportType;
import vn.edu.fpt.contactbook.service.UserImportWorkbooks;
import vn.edu.fpt.contactbook.support.ImportFiles;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.ImportFiles.row;

@IntegrationTest
class UserImportIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired LoginAttemptService loginAttemptService;

    @AfterEach
    void clearLoginAttempts() {
        loginAttemptService.reset("0915000001");
    }

    @ParameterizedTest
    @EnumSource(UserImportType.class)
    @DisplayName("ADMIN tải file mẫu: đúng content type, đúng tiêu đề, có dòng ví dụ và sheet Danh mục")
    void downloadTemplate(UserImportType type) throws Exception {
        byte[] file = mockMvc.perform(get("/admin/users/import/template").param("type", type.name())
                        .with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(UserImportWorkbooks.CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition", containsString("mau-nhap-" + type.slug() + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            for (int i = 0; i < type.columns().size(); i++) {
                assertThat(header.getCell(i).getStringCellValue()).isEqualTo(type.columns().get(i).header());
            }
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("0900000000");
            Sheet catalog = workbook.getSheet("Danh mục");
            assertThat(catalog).isNotNull();
            assertThat(catalog.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã khối");
            assertThat(catalog.getRow(1).getCell(0).getStringCellValue()).isNotBlank();
        }
    }

    @Test
    @DisplayName("File học sinh hợp lệ 2 dòng: trả file kết quả, tạo 2 người, mật khẩu tạm đăng nhập được và bị ép đổi")
    void importValidStudents() throws Exception {
        long auditBefore = auditLogRepository.count();
        byte[] file = ImportFiles.xlsx(UserImportType.STUDENT, List.of(
                student("0915000001", "HS900001"),
                student(915000002L, "HS900002")));

        MvcResult result = mockMvc.perform(upload(UserImportType.STUDENT, "sinh-vien.xlsx", file))
                .andExpect(status().isOk())
                .andExpect(content().contentType(UserImportWorkbooks.CONTENT_TYPE))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", containsString("ket-qua-nhap-sinh-vien-")))
                .andReturn();

        List<List<String>> rows = readRows(result.getResponse().getContentAsByteArray());
        assertThat(rows.getFirst()).containsExactly("Số điện thoại", "Họ tên", "Mã HS", "Mật khẩu tạm");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).startsWith("0915000001", "Nguyễn Nhập Một", "HS900001");
        assertThat(rows.get(2).getFirst()).as("ô số vẫn giữ đủ 10 chữ số").isEqualTo("0915000002");
        String temporaryPassword = rows.get(1).get(3);
        assertThat(temporaryPassword).hasSize(12);

        for (String phone : List.of("0915000001", "0915000002")) {
            User user = userRepository.findByPhone(phone).orElseThrow();
            assertThat(user.isMustChangePassword()).isTrue();
            assertThat(userRoleRepository.findByUserId(user.getId()))
                    .extracting(ur -> ur.getRole().getCode()).containsExactly("STUDENT");
            assertThat(studentRepository.findByUserId(user.getId())).isPresent();
        }
        assertThat(auditLogRepository.count()).isEqualTo(auditBefore + 3);
        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditActions.IMPORT_USERS);
            assertThat(entry.getDetail()).isEqualTo("type=STUDENT,count=2");
        });
        assertThat(auditLogRepository.findAll())
                .noneMatch(entry -> entry.getDetail() != null && entry.getDetail().contains(temporaryPassword));

        MvcResult login = mockMvc.perform(formLogin("/login").userParameter("phone").user("0915000001")
                        .password(temporaryPassword))
                .andExpect(redirectedUrl("/student"))
                .andReturn();
        mockMvc.perform(get("/student").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(redirectedUrl("/change-password"));
    }

    @Test
    @DisplayName("File giáo viên hợp lệ: chỉ gán vai trò TEACHER (tổ TT)")
    void importValidTeacher() throws Exception {
        byte[] file = ImportFiles.xlsx(UserImportType.TEACHER, List.of(
                row("0915000010", "Giáo Viên Nhập", "Nữ", "15/03/1985", "gv.nhap@fpt.edu.vn", null, "gv910", "tt",
                        "ThS.")));

        mockMvc.perform(upload(UserImportType.TEACHER, "giang-vien.xlsx", file))
                .andExpect(content().contentType(UserImportWorkbooks.CONTENT_TYPE));

        User user = userRepository.findByPhone("0915000010").orElseThrow();
        assertThat(userRoleRepository.findByUserId(user.getId()))
                .extracting(ur -> ur.getRole().getCode() + "@" + ur.getDepartment().getCode())
                .containsExactly("TEACHER@TT");
    }

    @Test
    @DisplayName("File có 1 dòng lỗi: bảng lỗi đúng dòng, cột, lý do; không người dùng nào được tạo")
    void oneInvalidRowCreatesNobody() throws Exception {
        List<Object> bad = student("0915000022", "HS900022");
        bad.set(UserImportType.STUDENT.columns().indexOf(ImportColumn.MAJOR_CODE), "XX");
        byte[] file = ImportFiles.xlsx(UserImportType.STUDENT, List.of(
                student("0915000021", "HS900021"),
                bad,
                student("0915000023", "HS900023")));

        mockMvc.perform(upload(UserImportType.STUDENT, "sinh-vien.xlsx", file))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(allOf(
                        containsString("File có 1 lỗi, chưa người dùng nào được tạo"),
                        containsString("<td>3</td>"),
                        containsString("<td>Mã khối</td>"),
                        containsString("Mã khối &quot;XX&quot; không tồn tại"))));

        for (String phone : List.of("0915000021", "0915000022", "0915000023")) {
            assertThat(userRepository.findByPhone(phone)).as(phone).isEmpty();
        }
    }

    @Test
    @DisplayName("Trùng với người dùng đã có (0900000004) và trùng mã HS260001 báo cạnh đúng cột")
    void duplicatesWithExistingData() throws Exception {
        byte[] file = ImportFiles.xlsx(UserImportType.STUDENT, List.of(student(TestAccounts.STUDENT, "hs260001")));

        mockMvc.perform(upload(UserImportType.STUDENT, "sinh-vien.xlsx", file))
                .andExpect(content().string(allOf(
                        containsString("Số điện thoại đã được sử dụng"),
                        containsString("Mã học sinh đã tồn tại"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"danh-sach.csv", "danh-sach.xls", "gia-mao.xlsx"})
    @DisplayName("File không phải .xlsx thật bị từ chối")
    void rejectsNonXlsx(String fileName) throws Exception {
        mockMvc.perform(upload(UserImportType.STUDENT, fileName, "Số điện thoại,Họ tên\n0915000030,A".getBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chỉ nhận file Excel .xlsx")));
    }

    @Test
    @DisplayName("Không chọn file thì báo lỗi")
    void missingFile() throws Exception {
        mockMvc.perform(multipart("/admin/users/import").param("type", "STUDENT")
                        .with(as(TestAccounts.ADMIN)).with(csrf()))
                .andExpect(content().string(containsString("Vui lòng chọn file Excel để tải lên")));
    }

    @Test
    @DisplayName("Trang nhập có cảnh báo mật khẩu; danh sách có nút Nhập từ Excel cho ADMIN")
    void pageAndButton() throws Exception {
        mockMvc.perform(get("/admin/users/import").with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("chứa mật khẩu tạm"),
                        containsString("/admin/users/import/template?type=TEACHER"))));
        mockMvc.perform(get("/admin/users").with(as(TestAccounts.ADMIN)))
                .andExpect(content().string(containsString("Nhập từ Excel")));
        mockMvc.perform(get("/admin/users").with(as(TestAccounts.TEACHER_HEAD)))
                .andExpect(content().string(not(containsString("Nhập từ Excel"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {TestAccounts.TEACHER_HEAD, TestAccounts.TEACHER, TestAccounts.STUDENT, TestAccounts.PARENT})
    @DisplayName("Vai trò khác ADMIN bị 403 ở mọi URL nhập")
    void otherRolesAreForbidden(String phone) throws Exception {
        mockMvc.perform(get("/admin/users/import").with(as(phone))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users/import/template").param("type", "STUDENT").with(as(phone)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/admin/users/import")
                        .file(new MockMultipartFile("file", "a.xlsx", UserImportWorkbooks.CONTENT_TYPE,
                                ImportFiles.xlsx(UserImportType.STUDENT, List.of(student("0915000040", "HS900040")))))
                        .param("type", "STUDENT").with(as(phone)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByPhone("0915000040")).isEmpty();
    }

    @Test
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get("/admin/users/import")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/admin/users/import/template").param("type", "STUDENT")).andExpect(redirectedUrl("/login"));
    }

    private RequestBuilder upload(UserImportType type, String fileName, byte[] content) {
        return multipart("/admin/users/import")
                .file(new MockMultipartFile("file", fileName, UserImportWorkbooks.CONTENT_TYPE, content))
                .param("type", type.name())
                .with(as(TestAccounts.ADMIN)).with(csrf());
    }

    /** Dòng sinh viên hợp lệ, ngành SE, lớp 10A1, ngày sinh ô kiểu ngày. */
    private static List<Object> student(Object phone, String code) {
        String name = switch (String.valueOf(phone)) {
            case "0915000001" -> "Nguyễn Nhập Một";
            default -> "Học Sinh Nhập";
        };
        return new ArrayList<>(row(phone, name, "Nam", LocalDate.of(2006, 9, 1), null, "Hà Nội", code, "K10",
                "10A1", "2026", "Đang học"));
    }

    private static List<List<String>> readRows(byte[] file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            for (Row row : workbook.getSheetAt(0)) {
                List<String> values = new ArrayList<>();
                row.forEach(cell -> values.add(cell.getStringCellValue()));
                rows.add(values);
            }
        }
        return rows;
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
