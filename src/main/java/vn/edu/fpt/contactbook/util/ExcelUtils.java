package vn.edu.fpt.contactbook.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * Đọc ô Excel theo chữ người dùng nhìn thấy (DataFormatter), để không mất số 0 đầu hay đổi định dạng.
 */
public final class ExcelUtils {

    /** Nhận cả 01/09/2006 lẫn 1/9/2006; STRICT để 31/02/2006 bị từ chối. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private ExcelUtils() {
    }

    /** Chữ hiển thị của ô, bỏ khoảng trắng hai đầu, chuẩn Unicode NFC; null nếu trống. */
    public static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        String value = Normalizer.normalize(new DataFormatter().formatCellValue(cell), Normalizer.Form.NFC).strip();
        return value.isEmpty() ? null : value;
    }

    /**
     * Số điện thoại. Ô kiểu số làm Excel bỏ số 0 đầu (0900000001 thành 900000001), nên với ô số
     * đủ 9 chữ số thì thêm lại số 0; các dạng khác để PhoneUtils chuẩn hóa và kiểm tra.
     */
    public static String phone(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            String digits = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            return digits.matches("\\d{9}") ? "0" + digits : digits;
        }
        return text(cell);
    }

    /**
     * Ngày: ô kiểu ngày của Excel hoặc chữ dd/MM/yyyy. null nếu trống.
     *
     * @throws DateTimeException nếu ô có giá trị nhưng không phải ngày hợp lệ
     */
    public static LocalDate date(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            throw new DateTimeException("Numeric cell is not formatted as a date");
        }
        String value = text(cell);
        return value == null ? null : LocalDate.parse(value, DATE);
    }

    /**
     * Số thập phân (ví dụ điểm): ô kiểu số đọc trực tiếp giá trị (không qua DataFormatter, vốn có thể làm tròn theo
     * định dạng ô), ô chữ nhận cả dấu chấm lẫn dấu phẩy thập phân. null nếu trống.
     *
     * @throws NumberFormatException nếu ô có giá trị nhưng không phải số
     */
    public static BigDecimal decimal(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                throw new NumberFormatException("Date cell is not a number");
            }
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String value = text(cell);
        return value == null ? null : new BigDecimal(value.replace(',', '.'));
    }

    /** Dòng không có ô nào có chữ trong columnCount cột đầu. */
    public static boolean isBlankRow(Row row, int columnCount) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < columnCount; i++) {
            if (text(row.getCell(i)) != null) {
                return false;
            }
        }
        return true;
    }
}
