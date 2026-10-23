package vn.edu.fpt.contactbook.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.dto.UserImportResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.support.ImportFiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edu.fpt.contactbook.support.ImportFiles.row;

/**
 * Đọc và kiểm tra file nhập với repository giả lập; UserService, RoleProfiles, Validator là code thật,
 * nên các quy tắc kiểm tra là đúng bộ quy tắc của tạo tay.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserImportServiceTest {

    private static final AppUserPrincipal ADMIN = UserServiceTest.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock StudentRepository studentRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock MajorRepository majorRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private UserImportService service;

    @BeforeEach
    void setUp() {
        RoleProfiles roleProfiles = new RoleProfiles(studentRepository, teacherRepository, majorRepository,
                schoolClassRepository, departmentRepository);
        UserAccessPolicy accessPolicy = new UserAccessPolicy(teacherRepository);
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        UserService userService = new UserService(userRepository, userRoleRepository, roleRepository,
                studentRepository, teacherRepository, parentRepository, parentStudentRepository, roleProfiles,
                accessPolicy, new TemporaryPasswordGenerator(), new BCryptPasswordEncoder(4), auditService);
        service = new UserImportService(userService, accessPolicy, validator, majorRepository,
                schoolClassRepository, departmentRepository, auditService);

        Major se = major(1L, "SE");
        Major ia = major(14L, "IA");
        SchoolClass se1901 = schoolClass(1L, "10A1", se);
        SchoolClass ia01 = schoolClass(20L, "IA1801", ia);
        Department seDepartment = new Department();
        seDepartment.setId(1L);
        seDepartment.setCode("SE");
        when(majorRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(ia, se));
        when(schoolClassRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(ia01, se1901));
        when(departmentRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(seDepartment));
        when(majorRepository.findById(1L)).thenReturn(Optional.of(se));
        when(majorRepository.findById(14L)).thenReturn(Optional.of(ia));
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(se1901));
        when(schoolClassRepository.findById(20L)).thenReturn(Optional.of(ia01));
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(departmentRepository.getReferenceById(1L)).thenReturn(seDepartment);
        for (String code : UserService.ROLE_ORDER) {
            Role role = new Role();
            role.setCode(code);
            when(roleRepository.findByCode(code)).thenReturn(Optional.of(role));
        }
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("Số điện thoại ô Text và ô Numeric, ngày ô kiểu ngày và ô chữ đều đọc đúng; tạo đủ người")
    void readsTextAndNumericPhonesAndBothDateForms() {
        UserImportResult result = importStudents(
                student("0911000001", LocalDate.of(2006, 9, 1), "SE000001"),
                student(911000002L, "15/03/2006", "SE000002"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).extracting(user -> user.phone())
                .containsExactly("0911000001", "0911000002");
        assertThat(result.created()).allSatisfy(user -> assertThat(user.temporaryPassword()).hasSize(12));
        verify(userRepository, atLeastOnce()).existsByPhone("0911000002");
    }

    @Test
    @DisplayName("Dòng trống được bỏ qua; số dòng báo lỗi đúng như Excel (tiêu đề là dòng 1)")
    void blankRowsAreSkippedAndRowNumbersMatchExcel() {
        UserImportResult result = importStudents(
                student("0911000001", null, "SE000001"),
                null,
                student("12345", null, "SE000003"));

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(4);
            assertThat(error.column()).isEqualTo("Số điện thoại");
        });
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sai tiêu đề cột: báo ở dòng 1 với tên cột đúng, không đọc tiếp")
    void wrongHeader() {
        List<Object> header = new ArrayList<>(UserImportType.STUDENT.columns().stream().map(ImportColumn::header).toList());
        header.set(1, "Tên");
        byte[] file = ImportFiles.xlsxWithHeader(header, List.of(student("0911000001", null, "SE1")));

        UserImportResult result = service.importUsers(ADMIN, UserImportType.STUDENT, "a.xlsx", file, null);

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.column()).isEqualTo("Họ tên");
            assertThat(error.message()).contains("\"Tên\"");
        });
    }

    @Test
    @DisplayName("Trùng trong file: số điện thoại (khác cách viết), email (khác hoa thường), mã SV")
    void duplicatesInsideFile() {
        List<Object> first = student("0911000001", null, "SE000001");
        first.set(4, "a@fpt.edu.vn");
        List<Object> second = student("+84 911 000 001", null, "se000001");
        second.set(4, "A@FPT.EDU.VN");

        UserImportResult result = importStudents(first, second);

        assertThat(result.errors()).extracting(ImportRowError::rowNumber).containsOnly(3);
        assertThat(result.errors()).extracting(ImportRowError::column)
                .containsExactly("Số điện thoại", "Email", "Mã SV");
        assertThat(result.errors()).extracting(ImportRowError::message).containsOnly("Trùng với dòng 2 trong file");
    }

    @Test
    @DisplayName("Mã ngành, mã lớp, mã bộ môn không tồn tại")
    void unknownCodes() {
        List<Object> student = student("0911000001", null, "SE000001");
        student.set(7, "XX");
        student.set(8, "ZZ99");
        UserImportResult students = importStudents(student);
        assertThat(students.errors()).extracting(ImportRowError::message).containsExactly(
                "Mã ngành \"XX\" không tồn tại (xem sheet Danh mục)",
                "Mã lớp \"ZZ99\" không tồn tại (xem sheet Danh mục)");

        byte[] teacherFile = ImportFiles.xlsx(UserImportType.TEACHER,
                List.of(row("0911000002", "GV Mới", "Nữ", null, null, null, "GV900", "NOPE", null)));
        assertThat(service.importUsers(ADMIN, UserImportType.TEACHER, "g.xlsx", teacherFile, null).errors())
                .singleElement().extracting(ImportRowError::column).isEqualTo("Mã bộ môn");
    }

    @Test
    @DisplayName("Lớp không thuộc ngành của sinh viên")
    void classOfOtherMajor() {
        List<Object> student = student("0911000001", null, "SE000001");
        student.set(8, "IA1801");

        assertThat(importStudents(student).errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("Mã lớp");
            assertThat(error.message()).isEqualTo("Lớp không thuộc ngành đã chọn");
        });
    }

    @Test
    @DisplayName("Trùng với dữ liệu đã có, giới tính và năm nhập học sai; một dòng nhiều lỗi được báo đủ")
    void existingDataAndCellFormats() {
        when(userRepository.existsByPhone("0911000001")).thenReturn(true);
        when(studentRepository.existsByStudentCode("SE000001")).thenReturn(true);
        List<Object> student = student("0911000001", null, "SE000001");
        student.set(2, "Trai");
        student.set(9, "20x6");

        assertThat(importStudents(student).errors()).extracting(ImportRowError::column, ImportRowError::message)
                .containsExactly(
                        tuple("Số điện thoại", "Số điện thoại đã được sử dụng"),
                        tuple("Giới tính", "Giới tính chỉ nhận Nam, Nữ hoặc Khác"),
                        tuple("Mã SV", "Mã sinh viên đã tồn tại"),
                        tuple("Năm nhập học", "Năm nhập học phải là số có 4 chữ số"));
    }

    @Test
    @DisplayName("Dòng ví dụ của file mẫu chưa xóa bị báo lỗi")
    void exampleRowIsRejected() {
        assertThat(importStudents(student(UserImportService.EXAMPLE_PHONE, null, "SE999999")).errors())
                .extracting(ImportRowError::message)
                .contains("Đây là dòng ví dụ của file mẫu, hãy xóa trước khi nhập");
    }

    @Test
    @DisplayName("Tối đa 500 dòng dữ liệu: 500 dòng được đọc, 501 dòng bị từ chối")
    void rowLimit() {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < UserImportService.MAX_ROWS; i++) {
            rows.add(student(String.format("0912%06d", i), null, String.format("SE%06d", i)));
        }
        byte[] exactlyMax = ImportFiles.xlsx(UserImportType.STUDENT, rows);
        assertThat(service.read(UserImportType.STUDENT, "a.xlsx", exactlyMax, new ArrayList<>())).hasSize(500);

        rows.add(student("0913000000", null, "SE999998"));
        byte[] tooMany = ImportFiles.xlsx(UserImportType.STUDENT, rows);
        assertThatThrownBy(() -> service.importUsers(ADMIN, UserImportType.STUDENT, "a.xlsx", tooMany, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá 500 dòng");
    }

    @Test
    @DisplayName("Chỉ nhận .xlsx thật; file rỗng hoặc chỉ có tiêu đề bị từ chối")
    void fileLevelErrors() {
        byte[] valid = ImportFiles.xlsx(UserImportType.STUDENT, List.of(student("0911000001", null, "SE1")));
        assertThatThrownBy(() -> service.importUsers(ADMIN, UserImportType.STUDENT, "a.csv", valid, null))
                .hasMessage("Chỉ nhận file Excel .xlsx");
        assertThatThrownBy(() -> service.importUsers(ADMIN, UserImportType.STUDENT, "a.xlsx",
                "phone,name\n0911000001,A".getBytes(), null))
                .hasMessage("Chỉ nhận file Excel .xlsx (không mở được file)");
        assertThatThrownBy(() -> service.importUsers(ADMIN, UserImportType.STUDENT, "a.xlsx", new byte[0], null))
                .hasMessage("File tải lên rỗng");
        assertThatThrownBy(() -> service.importUsers(ADMIN, UserImportType.STUDENT, "a.xlsx",
                ImportFiles.xlsx(UserImportType.STUDENT, List.of()), null))
                .hasMessage("File không có dòng dữ liệu nào");
    }

    @Test
    @DisplayName("Giảng viên: chỉ gán vai trò TEACHER")
    void teacherImportGetsTeacherRoleOnly() {
        byte[] file = ImportFiles.xlsx(UserImportType.TEACHER,
                List.of(row("0911000003", "GV Mới", "Nữ", "15/03/1985", null, null, "gv900", "se", "ThS.")));

        UserImportResult result = service.importUsers(ADMIN, UserImportType.TEACHER, "g.xlsx", file, null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).singleElement().extracting(user -> user.code()).isEqualTo("GV900");
        verify(roleRepository, never()).findByCode(RoleCodes.HEAD_OF_DEPARTMENT);
        verify(roleRepository).findByCode(RoleCodes.TEACHER);
    }

    @SafeVarargs
    private UserImportResult importStudents(List<Object>... rows) {
        byte[] file = ImportFiles.xlsx(UserImportType.STUDENT, Arrays.asList(rows));
        return service.importUsers(ADMIN, UserImportType.STUDENT, "sinh-vien.xlsx", file, "10.0.0.1");
    }

    /** Dòng sinh viên hợp lệ (ngành SE, lớp 10A1); phone/dob có thể là chữ, số hoặc ngày. */
    private static List<Object> student(Object phone, Object dateOfBirth, String code) {
        return new ArrayList<>(row(phone, "Sinh Viên Mới", "Nam", dateOfBirth, null, null, code, "SE", "10A1",
                "2026", null));
    }

    private static Major major(Long id, String code) {
        Major major = new Major();
        major.setId(id);
        major.setCode(code);
        return major;
    }

    private static SchoolClass schoolClass(Long id, String code, Major major) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setCode(code);
        schoolClass.setMajor(major);
        return schoolClass;
    }
}
