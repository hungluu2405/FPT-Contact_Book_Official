package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.GradeImportResult;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.GradeService.CellUpdate;
import vn.edu.fpt.contactbook.service.GradeService.Loaded;
import vn.edu.fpt.contactbook.util.ExcelUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nhập điểm từ Excel (SRS FR-GRADE-03), cùng cách làm với nhập người dùng (3b) và ghi danh (4b): cột "Mã SV" và một
 * cột cho mỗi đầu điểm của môn (tiêu đề đúng tên đầu điểm); kiểm tra TOÀN BỘ file trước, có lỗi thì không ghi ô nào
 * và trả bảng lỗi theo dòng. Ô trống trong file là không thay đổi; có giá trị ở ô đã công bố là lỗi. Quyền, khóa lớp
 * và điều kiện tổng trọng số 100% giống nhập tay (GradeService.lockForEntry).
 */
@Service
@RequiredArgsConstructor
public class GradeImportService {

    public static final String CONTENT_TYPE = EnrollmentImportService.CONTENT_TYPE;
    public static final String COLUMN = "Mã SV";
    public static final int MAX_ROWS = ExcelImportReader.MAX_ROWS;

    private final GradeService gradeService;
    private final AuditService auditService;

    /** Một dòng dữ liệu: số dòng Excel, mã SV đã chuẩn hóa, mỗi đầu điểm một ô theo thứ tự cột. */
    record ParsedRow(int rowNumber, String studentCode, List<ParsedCell> cells) {
    }

    /** value null và invalid false là ô trống (không thay đổi). */
    record ParsedCell(BigDecimal value, boolean invalid) {
    }

    @Transactional
    public GradeImportResult importFile(AppUserPrincipal actor, Long sectionId, String fileName, byte[] content,
                                        String ipAddress) {
        return GradeService.guarded(() -> {
            Loaded data = gradeService.lockForEntry(actor, sectionId);
            List<GradeComponent> components = data.components();
            List<ImportRowError> headerErrors = new ArrayList<>();
            List<ParsedRow> rows = ExcelImportReader.read(fileName, content, headers(components),
                    "nhập điểm của lớp này", headerErrors, () -> row -> parse(row, components.size()));
            if (!headerErrors.isEmpty()) {
                return GradeImportResult.failed(headerErrors);
            }
            List<CellUpdate> updates = new ArrayList<>();
            List<ImportRowError> errors = validate(rows, data, updates);
            if (!errors.isEmpty()) {
                return GradeImportResult.failed(errors);
            }
            List<String> changes = gradeService.apply(actor, data.grades(), updates);
            auditService.log(actor.getUserId(), AuditActions.IMPORT_GRADES, AuditActions.ENTITY_CLASS_SECTION,
                    sectionId, "section=" + data.section().getCode() + ", rows=" + rows.size() + ", changes="
                            + changes, ipAddress);
            return new GradeImportResult(List.of(), changes.size());
        });
    }

    /**
     * Lỗi theo dòng: thiếu mã, trùng dòng, sinh viên không đang học lớp này; theo ô: không phải số, ngoài
     * 0..max_score, quá 2 chữ số thập phân, có giá trị ở ô đã công bố. Ô hợp lệ có giá trị được thêm vào updates.
     */
    static List<ImportRowError> validate(List<ParsedRow> rows, Loaded data, List<CellUpdate> updates) {
        Map<String, Enrollment> enrollments = data.enrollments().stream()
                .collect(Collectors.toMap(e -> e.getStudent().getStudentCode().toUpperCase(Locale.ROOT),
                        Function.identity()));
        List<GradeComponent> components = data.components();
        List<ImportRowError> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (ParsedRow row : rows) {
            String code = row.studentCode();
            if (code == null) {
                errors.add(new ImportRowError(row.rowNumber(), COLUMN, "Vui lòng nhập mã sinh viên"));
                continue;
            }
            Integer first = seen.putIfAbsent(code, row.rowNumber());
            if (first != null) {
                errors.add(new ImportRowError(row.rowNumber(), COLUMN, "Trùng với dòng " + first + " trong file"));
                continue;
            }
            Enrollment enrollment = enrollments.get(code);
            if (enrollment == null) {
                errors.add(new ImportRowError(row.rowNumber(), COLUMN,
                        "Sinh viên \"" + code + "\" không đang học lớp học phần này"));
                continue;
            }
            for (int i = 0; i < components.size(); i++) {
                GradeComponent component = components.get(i);
                ParsedCell cell = row.cells().get(i);
                String error = null;
                if (cell.invalid()) {
                    error = GradeMessages.SCORE_INVALID;
                } else if (cell.value() != null) {
                    Grade existing = data.grades().get(GradeSheet.key(enrollment.getId(), component.getId()));
                    error = existing != null && existing.isPublished()
                            ? "Ô điểm đã công bố, giảng viên không sửa được (để trống ô này)"
                            : GradeRule.validate(cell.value(), component.getMaxScore());
                    if (error == null) {
                        updates.add(new CellUpdate(enrollment, component, cell.value()));
                    }
                }
                if (error != null) {
                    errors.add(new ImportRowError(row.rowNumber(), component.getName(), error));
                }
            }
        }
        return errors;
    }

    /** File mẫu: tiêu đề Mã SV + tên các đầu điểm, mã các sinh viên đang học điền sẵn, sheet hướng dẫn. */
    @Transactional(readOnly = true)
    public byte[] template(AppUserPrincipal actor, Long sectionId) {
        Loaded data = gradeService.loadForTeacher(actor, sectionId);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);

            Sheet sheet = workbook.createSheet("Điểm");
            List<String> headers = headers(data.components());
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
                headerRow.getCell(i).setCellStyle(header);
                sheet.setColumnWidth(i, 22 * 256);
            }
            for (int i = 0; i < data.enrollments().size(); i++) {
                sheet.createRow(i + 1).createCell(0).setCellValue(
                        data.enrollments().get(i).getStudent().getStudentCode());
            }

            Sheet guide = workbook.createSheet("Hướng dẫn");
            String[] lines = {
                    "Mỗi dòng một sinh viên: mã sinh viên ở cột A, điểm từng đầu điểm ở các cột sau; giữ nguyên dòng tiêu đề.",
                    "Điểm từ 0 đến điểm tối đa của đầu điểm, tối đa 2 chữ số thập phân (8.5 hoặc 8,5).",
                    "Ô để trống: giữ nguyên điểm hiện tại. Ô điểm đã công bố phải để trống.",
                    "Tối đa " + MAX_ROWS + " dòng, file .xlsx tối đa 2 MB.",
                    "Có bất kỳ lỗi nào thì không ô nào được ghi; sửa lỗi theo bảng rồi tải lên lại."};
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            guide.setColumnWidth(0, 110 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> headers(List<GradeComponent> components) {
        return Stream.concat(Stream.of(COLUMN), components.stream().map(GradeComponent::getName)).toList();
    }

    private static ParsedRow parse(Row row, int componentCount) {
        List<ParsedCell> cells = new ArrayList<>();
        for (int i = 1; i <= componentCount; i++) {
            try {
                cells.add(new ParsedCell(ExcelUtils.decimal(row.getCell(i)), false));
            } catch (NumberFormatException e) {
                cells.add(new ParsedCell(null, true));
            }
        }
        return new ParsedRow(row.getRowNum() + 1, CatalogSupport.normalizeCode(ExcelUtils.text(row.getCell(0))),
                cells);
    }
}
