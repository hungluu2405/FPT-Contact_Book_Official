package vn.edu.fpt.contactbook.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Ghi báo cáo ra .xlsx (Apache POI), tạo trong bộ nhớ. Mỗi sheet: các dòng tiêu đề (tên báo cáo, bộ lọc), một dòng
 * trống, dòng tên cột (in đậm) rồi dữ liệu. Ô số (Number) ghi kiểu số; mọi giá trị khác ghi kiểu chuỗi, nên nội dung
 * do người dùng nhập (tên, mã) không bao giờ thành công thức.
 */
final class ReportWorkbooks {

    private static final int COLUMN_WIDTH = 18 * 256;

    record SheetData(String name, List<String> titles, List<String> headers, List<List<Object>> rows) {
    }

    private ReportWorkbooks() {
    }

    static byte[] write(List<SheetData> sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (SheetData data : sheets) {
                Sheet sheet = workbook.createSheet(data.name());
                int rowIndex = 0;
                for (String title : data.titles()) {
                    sheet.createRow(rowIndex++).createCell(0).setCellValue(title);
                }
                rowIndex++;
                Row header = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.headers().size(); i++) {
                    Cell cell = header.createCell(i);
                    cell.setCellValue(data.headers().get(i));
                    cell.setCellStyle(bold);
                    sheet.setColumnWidth(i, COLUMN_WIDTH);
                }
                for (List<Object> values : data.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int i = 0; i < values.size(); i++) {
                        set(row.createCell(i), values.get(i));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tên file: các phần nối bằng "-", ký tự ngoài chữ không dấu, số, "-", "_" đổi thành "_". */
    static String fileName(String... parts) {
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append('-');
            }
            name.append(part.replaceAll("[^A-Za-z0-9_-]", "_"));
        }
        return name.append(".xlsx").toString();
    }

    private static void set(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
