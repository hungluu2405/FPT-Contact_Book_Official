package vn.edu.fpt.contactbook.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.edu.fpt.contactbook.service.ImportColumn;
import vn.edu.fpt.contactbook.service.UserImportType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sinh file .xlsx cho test ngay trong bộ nhớ (không commit file nhị phân).
 * Giá trị String → ô Text, Number → ô Numeric, LocalDate → ô kiểu ngày; null → ô trống;
 * một dòng null → dòng trống hoàn toàn.
 */
public final class ImportFiles {

    private ImportFiles() {
    }

    public static byte[] xlsx(UserImportType type, List<List<Object>> rows) {
        List<Object> header = new ArrayList<>(type.columns().stream().map(ImportColumn::header).toList());
        return xlsxWithHeader(header, rows);
    }

    public static byte[] xlsxWithHeader(List<Object> header, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            write(sheet.createRow(0), header, dateStyle);
            for (int i = 0; i < rows.size(); i++) {
                List<Object> values = rows.get(i);
                if (values != null) {
                    write(sheet.createRow(i + 1), values, dateStyle);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static void write(Row row, List<Object> values, CellStyle dateStyle) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(i);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }
}
