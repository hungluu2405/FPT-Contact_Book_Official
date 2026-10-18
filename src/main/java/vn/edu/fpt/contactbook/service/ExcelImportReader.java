package vn.edu.fpt.contactbook.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.util.ExcelUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Phần đọc file dùng chung cho các chức năng nhập Excel (người dùng, ghi danh): chỉ nhận .xlsx, tối đa 2 MB,
 * kiểm tra tiêu đề cột ở dòng 1, bỏ dòng trống, tối đa 500 dòng dữ liệu. Lỗi mức file ném BusinessException;
 * sai tiêu đề thì ghi vào headerErrors và trả danh sách rỗng.
 */
final class ExcelImportReader {

    static final int MAX_ROWS = 500;
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private ExcelImportReader() {
    }

    /**
     * @param headers       tiêu đề các cột theo thứ tự, so khớp không phân biệt hoa thường
     * @param templateName  tên file mẫu dùng trong thông báo sai tiêu đề (ví dụ "sinh viên")
     * @param parserFactory chỉ được gọi khi tiêu đề đúng, để không nạp danh mục cho file sai mẫu
     */
    static <T> List<T> read(String fileName, byte[] content, List<String> headers, String templateName,
                            List<ImportRowError> headerErrors, Supplier<Function<Row, T>> parserFactory) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("Chỉ nhận file Excel .xlsx");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException("File tải lên rỗng");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException("File nhập tối đa 2 MB");
        }
        try (XSSFWorkbook workbook = open(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            checkHeader(headers, templateName, sheet.getRow(0), headerErrors);
            if (!headerErrors.isEmpty()) {
                return List.of();
            }
            Function<Row, T> parser = parserFactory.get();
            List<T> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (ExcelUtils.isBlankRow(row, headers.size())) {
                    continue;
                }
                if (rows.size() == MAX_ROWS) {
                    throw new BusinessException("File có quá " + MAX_ROWS + " dòng dữ liệu, hãy chia thành nhiều file");
                }
                rows.add(parser.apply(row));
            }
            if (rows.isEmpty()) {
                throw new BusinessException("File không có dòng dữ liệu nào");
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessException("Không đọc được file Excel");
        }
    }

    /** Mở .xlsx với cơ chế chống file độc hại mặc định của POI; file không phải .xlsx thật thì báo lỗi thân thiện. */
    private static XSSFWorkbook open(byte[] content) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("Chỉ nhận file Excel .xlsx (không mở được file)");
        }
    }

    private static void checkHeader(List<String> headers, String templateName, Row header,
                                    List<ImportRowError> errors) {
        for (int i = 0; i < headers.size(); i++) {
            String expected = headers.get(i);
            String actual = header == null ? null : ExcelUtils.text(header.getCell(i));
            if (actual == null || !actual.equalsIgnoreCase(expected)) {
                errors.add(new ImportRowError(1, expected, "Tiêu đề cột phải là \"" + expected + "\""
                        + (actual == null ? " (đang trống)" : " (đang là \"" + actual + "\")")
                        + ". Hãy dùng file mẫu " + templateName));
            }
        }
    }
}
