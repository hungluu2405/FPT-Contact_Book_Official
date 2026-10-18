package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.EnrollmentImportResult;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.ExcelUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nhập ghi danh từ Excel (một cột Mã SV), cùng cách làm với nhập người dùng ở giai đoạn 3b: kiểm tra TOÀN BỘ
 * file trước; có lỗi thì không ghi dòng nào và trả bảng lỗi theo dòng; vượt sĩ số là lỗi mức file.
 * Lớp học phần bị khóa dòng trong suốt quá trình kiểm tra và ghi, như ghi danh tay.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentImportService {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String COLUMN = "Mã SV";
    public static final int MAX_ROWS = ExcelImportReader.MAX_ROWS;

    private final EnrollmentService enrollmentService;
    private final StudentRepository studentRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    /** Một dòng dữ liệu: số dòng Excel và mã SV đã chuẩn hóa (trim, viết hoa). */
    record ParsedRow(int rowNumber, String studentCode) {
    }

    @Transactional
    public EnrollmentImportResult importFile(AppUserPrincipal actor, Long sectionId, String fileName, byte[] content,
                                             String ipAddress) {
        accessPolicy.requireAdmin(actor);
        ClassSection section = enrollmentService.lockOpenSection(sectionId);
        List<ImportRowError> headerErrors = new ArrayList<>();
        List<ParsedRow> rows = ExcelImportReader.read(fileName, content, List.of(COLUMN), "ghi danh", headerErrors,
                () -> row -> new ParsedRow(row.getRowNum() + 1, CatalogSupport.normalizeCode(
                        ExcelUtils.text(row.getCell(0)))));
        if (!headerErrors.isEmpty()) {
            return EnrollmentImportResult.failed(headerErrors);
        }

        Map<String, Student> students = studentRepository.findByStudentCodeIn(
                        rows.stream().map(ParsedRow::studentCode).distinct().toList()).stream()
                .collect(Collectors.toMap(s -> s.getStudentCode().toUpperCase(Locale.ROOT), Function.identity()));
        Map<Long, Enrollment> existing = enrollmentService.existingByStudent(sectionId,
                List.copyOf(students.values()));
        List<ImportRowError> errors = validate(rows, students, existing);
        if (!errors.isEmpty()) {
            return EnrollmentImportResult.failed(errors);
        }

        List<Student> toEnroll = rows.stream().map(row -> students.get(row.studentCode())).toList();
        enrollmentService.requireCapacity(section, toEnroll.size());
        int reEnrolled = (int) toEnroll.stream().filter(s -> existing.containsKey(s.getId())).count();
        enrollmentService.apply(section, toEnroll, existing);
        auditService.log(actor.getUserId(), AuditActions.IMPORT_ENROLLMENTS, AuditActions.ENTITY_CLASS_SECTION,
                sectionId, "count=" + toEnroll.size() + ", reEnrolled=" + reEnrolled, ipAddress);
        return new EnrollmentImportResult(List.of(), toEnroll.size() - reEnrolled, reEnrolled);
    }

    /** Lỗi theo dòng: mã không tồn tại, SV không đang học, đã có trong lớp, trùng dòng khác trong file. */
    static List<ImportRowError> validate(List<ParsedRow> rows, Map<String, Student> students,
                                         Map<Long, Enrollment> existing) {
        List<ImportRowError> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (ParsedRow row : rows) {
            String code = row.studentCode();
            Integer first = seen.putIfAbsent(code, row.rowNumber());
            String message;
            Student student = students.get(code);
            if (first != null) {
                message = "Trùng với dòng " + first + " trong file";
            } else if (student == null) {
                message = "Mã sinh viên \"" + code + "\" không tồn tại";
            } else if (student.getStatus() != StudentStatus.STUDYING) {
                message = SectionMessages.STUDENT_NOT_STUDYING;
            } else {
                Enrollment enrollment = existing.get(student.getId());
                message = enrollment == null || enrollment.getStatus() == EnrollmentStatus.DROPPED ? null
                        : enrollment.getStatus() == EnrollmentStatus.COMPLETED ? SectionMessages.ALREADY_COMPLETED
                        : SectionMessages.ALREADY_ENROLLED;
            }
            if (message != null) {
                errors.add(new ImportRowError(row.rowNumber(), COLUMN, message));
            }
        }
        return errors;
    }

    /** File mẫu: dòng tiêu đề "Mã SV" và sheet hướng dẫn; không có dòng ví dụ để không nhập nhầm. */
    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);

            Sheet sheet = workbook.createSheet("Ghi danh");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(COLUMN);
            row.getCell(0).setCellStyle(header);
            sheet.setColumnWidth(0, 20 * 256);

            Sheet guide = workbook.createSheet("Hướng dẫn");
            String[] lines = {
                    "Mỗi dòng một mã sinh viên ở cột A, bắt đầu từ dòng 2; giữ nguyên dòng tiêu đề.",
                    "Tối đa " + MAX_ROWS + " dòng, file .xlsx tối đa 2 MB.",
                    "Chỉ ghi danh sinh viên đang học; sinh viên đã bị gỡ khỏi lớp sẽ được ghi danh lại.",
                    "Có bất kỳ lỗi nào thì không sinh viên nào được ghi danh; sửa lỗi theo bảng rồi tải lên lại."};
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            guide.setColumnWidth(0, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
