package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.ImportedUser;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.util.AppTime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sinh file Excel cho chức năng nhập người dùng: file mẫu (kèm dòng ví dụ và sheet Danh mục) và file kết quả
 * có mật khẩu tạm. Chỉ tạo trong bộ nhớ, không lưu file trên server.
 */
@Component
@RequiredArgsConstructor
public class UserImportWorkbooks {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CATALOG_SHEET = "Danh mục";
    private static final int COLUMN_WIDTH = 20 * 256;

    private final CatalogLookupService catalogLookupService;

    public byte[] template(UserImportType type) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(type.label());
            List<ImportColumn> columns = type.columns();
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                ImportColumn column = columns.get(i);
                header.createCell(i).setCellValue(column.header());
                header.getCell(i).setCellStyle(styles.header);
                sheet.setColumnWidth(i, COLUMN_WIDTH);
                if (column.text()) {
                    // Cột Text: Excel giữ nguyên số 0 đầu của số điện thoại và các mã.
                    sheet.setDefaultColumnStyle(i, styles.text);
                }
            }
            Row example = sheet.createRow(1);
            List<String> values = exampleValues(type);
            for (int i = 0; i < values.size(); i++) {
                example.createCell(i).setCellValue(values.get(i));
                example.getCell(i).setCellStyle(columns.get(i).text() ? styles.exampleText : styles.example);
            }
            sheet.createFreezePane(0, 1);
            writeCatalog(workbook.createSheet(CATALOG_SHEET), styles);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** File kết quả trả về ngay sau khi nhập; chứa mật khẩu tạm nên không lưu ở đâu. */
    public byte[] result(UserImportType type, List<ImportedUser> users) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Kết quả");
            String codeHeader = type == UserImportType.STUDENT ? "Mã SV" : "Mã GV";
            List<String> headers = List.of("Số điện thoại", "Họ tên", codeHeader, "Mật khẩu tạm");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
                header.getCell(i).setCellStyle(styles.header);
                sheet.setColumnWidth(i, COLUMN_WIDTH);
            }
            int rowIndex = 1;
            for (ImportedUser user : users) {
                Row row = sheet.createRow(rowIndex++);
                List<String> values = List.of(user.phone(), user.fullName(), user.code(), user.temporaryPassword());
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i));
                    row.getCell(i).setCellStyle(styles.text);
                }
            }
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> exampleValues(UserImportType type) {
        String phone = UserImportService.EXAMPLE_PHONE;
        if (type == UserImportType.STUDENT) {
            List<OptionView> majors = catalogLookupService.majors();
            OptionView major = majors.isEmpty() ? null : majors.getFirst();
            String classCode = major == null ? "" : catalogLookupService.classes().stream()
                    .filter(c -> major.id().equals(c.groupId())).map(OptionView::code).findFirst().orElse("");
            return List.of(phone, "Nguyễn Văn Ví Dụ", "Nam", "01/09/2006", "vidu.sv@example.com", "Hà Nội",
                    "SE999999", major == null ? "" : major.code(), classCode,
                    String.valueOf(AppTime.now().getYear()), "Đang học");
        }
        List<OptionView> departments = catalogLookupService.departments();
        return List.of(phone, "Trần Thị Ví Dụ", "Nữ", "15/03/1985", "vidu.gv@example.com", "Đà Nẵng",
                "GV999", departments.isEmpty() ? "" : departments.getFirst().code(), "ThS.");
    }

    /** Sheet Danh mục: các mã hợp lệ, giá trị cho Giới tính/Trạng thái và lưu ý về dòng ví dụ. */
    private void writeCatalog(Sheet sheet, Styles styles) {
        List<OptionView> majors = catalogLookupService.majors();
        Map<Long, String> majorCodes = majors.stream()
                .collect(Collectors.toMap(OptionView::id, OptionView::code, (a, b) -> a));
        List<List<String>> blocks = List.of(
                column("Mã ngành", majors, OptionView::code), column("Tên ngành", majors, OptionView::name),
                column("Mã lớp", catalogLookupService.classes(), OptionView::code),
                column("Ngành của lớp", catalogLookupService.classes(), c -> majorCodes.getOrDefault(c.groupId(), "")),
                column("Mã bộ môn", catalogLookupService.departments(), OptionView::code),
                column("Tên bộ môn", catalogLookupService.departments(), OptionView::name),
                List.of("Giới tính", "Nam", "Nữ", "Khác"),
                List.of("Trạng thái (sinh viên)", "Đang học", "Bảo lưu", "Đã tốt nghiệp", "Thôi học"));
        int height = blocks.stream().mapToInt(List::size).max().orElse(0);
        for (int r = 0; r < height; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < blocks.size(); c++) {
                List<String> block = blocks.get(c);
                if (r < block.size()) {
                    row.createCell(c).setCellValue(block.get(r));
                    row.getCell(c).setCellStyle(r == 0 ? styles.header : styles.text);
                }
            }
        }
        for (int c = 0; c < blocks.size(); c++) {
            sheet.setColumnWidth(c, COLUMN_WIDTH);
        }
        Row note = sheet.createRow(height + 1);
        note.createCell(0).setCellValue("Lưu ý: xóa dòng ví dụ (số điện thoại " + UserImportService.EXAMPLE_PHONE
                + ") trước khi nhập. Ngày sinh dạng dd/MM/yyyy. Mã lớp và Trạng thái có thể để trống"
                + " (trạng thái mặc định Đang học). Tối đa " + UserImportService.MAX_ROWS + " dòng mỗi file.");
    }

    private static List<String> column(String title, List<OptionView> options, Function<OptionView, String> value) {
        return Stream.concat(Stream.of(title), options.stream().map(value)).toList();
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle text;
        final CellStyle example;
        final CellStyle exampleText;

        Styles(Workbook workbook) {
            short textFormat = workbook.createDataFormat().getFormat("@");
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setDataFormat(textFormat);
            text = workbook.createCellStyle();
            text.setDataFormat(textFormat);
            Font italic = workbook.createFont();
            italic.setItalic(true);
            italic.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            example = workbook.createCellStyle();
            example.setFont(italic);
            exampleText = workbook.createCellStyle();
            exampleText.setFont(italic);
            exampleText.setDataFormat(textFormat);
        }
    }
}
