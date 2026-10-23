package vn.edu.fpt.contactbook.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.EnrollmentImportResult;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.support.ImportFiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edu.fpt.contactbook.support.ImportFiles.row;

/**
 * Nhập ghi danh từ Excel: có lỗi thì không ghi dòng nào; lỗi theo dòng; vượt sĩ số là lỗi mức file.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentImportServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
            Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock StudentRepository studentRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private EnrollmentImportService service;
    private ClassSection section;
    private Student a;
    private Student b;
    private Student onLeave;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        UserAccessPolicy userAccessPolicy = new UserAccessPolicy(teacherRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(userAccessPolicy);
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        EnrollmentService enrollmentService = new EnrollmentService(classSectionRepository, enrollmentRepository,
                studentRepository, schoolClassRepository, classSectionService, userAccessPolicy, auditService);
        service = new EnrollmentImportService(enrollmentService, studentRepository, userAccessPolicy, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se),
                CatalogTestData.semester(1L, "HK1-2627"), CatalogTestData.teacher(2L, "GV002", se), 10, SectionStatus.OPEN);
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        a = CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING);
        b = CatalogTestData.student(2L, "HS250101", StudentStatus.STUDYING);
        onLeave = CatalogTestData.student(3L, "HS250102", StudentStatus.ON_LEAVE);
        when(studentRepository.findByStudentCodeIn(anyCollection())).thenReturn(List.of(a, b, onLeave));
        when(enrollmentRepository.findByClassSectionIdAndStudentIdIn(eq(10L), anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("File hợp lệ: mã được trim, viết hoa; HS DROPPED được ghi danh lại; ghi audit")
    void validFileEnrollsAll() {
        when(enrollmentRepository.findByClassSectionIdAndStudentIdIn(eq(10L), anyCollection())).thenReturn(List.of(
                CatalogTestData.enrollment(7L, section, b, EnrollmentStatus.DROPPED)));

        EnrollmentImportResult result = service.importFile(ADMIN, 10L, "ds.xlsx",
                file(row(" hs260001 "), row("HS250101")), "127.0.0.1");

        assertThat(result.success()).isTrue();
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.reEnrolled()).isEqualTo(1);
        verify(enrollmentRepository, times(1)).save(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Lỗi theo dòng: mã không tồn tại, HS không đang học, đã có trong lớp, trùng trong file; không ghi ai")
    void rowErrorsWriteNothing() {
        when(enrollmentRepository.findByClassSectionIdAndStudentIdIn(eq(10L), anyCollection())).thenReturn(List.of(
                CatalogTestData.enrollment(7L, section, b, EnrollmentStatus.ENROLLED)));

        EnrollmentImportResult result = service.importFile(ADMIN, 10L, "ds.xlsx",
                file(row("HS260001"), row("SE999999"), row("HS250102"), row("HS250101"), row("hs260001")),
                "127.0.0.1");

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).extracting(ImportRowError::rowNumber, ImportRowError::message).containsExactly(
                tuple(3, "Mã học sinh \"SE999999\" không tồn tại"),
                tuple(4, SectionMessages.STUDENT_NOT_STUDYING),
                tuple(5, SectionMessages.ALREADY_ENROLLED),
                tuple(6, "Trùng với dòng 2 trong file"));
        verify(enrollmentRepository, never()).save(any());
        verify(enrollmentRepository, never()).flush();
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Vượt sĩ số là lỗi mức file, không ghi ai")
    void overCapacityRejectsFile() {
        section.setMaxStudents((short) 2);
        when(enrollmentRepository.countByClassSectionIdAndStatus(10L, EnrollmentStatus.ENROLLED)).thenReturn(1L);

        assertThatThrownBy(() -> service.importFile(ADMIN, 10L, "ds.xlsx", file(row("HS260001"), row("HS250101")),
                "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không đủ chỗ: cần thêm 2 học sinh nhưng lớp chỉ còn 1 chỗ");
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void wrongHeaderIsReported() {
        byte[] content = ImportFiles.xlsxWithHeader(List.of("Mã học sinh"), List.of(row("HS260001")));

        EnrollmentImportResult result = service.importFile(ADMIN, 10L, "ds.xlsx", content, "127.0.0.1");

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(1);
            assertThat(error.message()).contains("Mã HS").contains("ghi danh");
        });
    }

    @Test
    void closedSectionAndNonAdminRejected() {
        section.setStatus(SectionStatus.CLOSED);
        assertThatThrownBy(() -> service.importFile(ADMIN, 10L, "ds.xlsx", file(row("HS260001")), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(SectionMessages.SECTION_NOT_OPEN);
        assertThatThrownBy(() -> service.importFile(HEAD_SE, 10L, "ds.xlsx", file(row("HS260001")), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void notXlsxRejected() {
        assertThatThrownBy(() -> service.importFile(ADMIN, 10L, "ds.csv", file(row("HS260001")), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage("Chỉ nhận file Excel .xlsx");
    }

    @Test
    @DisplayName("File mẫu có tiêu đề Mã HS và sheet hướng dẫn, không có dòng ví dụ")
    void templateHasHeaderOnly() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã HS");
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
            assertThat(workbook.getSheetName(1)).isEqualTo("Hướng dẫn");
        }
    }

    @SafeVarargs
    private static byte[] file(List<Object>... rows) {
        return ImportFiles.xlsxWithHeader(List.of(EnrollmentImportService.COLUMN), List.of(rows));
    }
}
