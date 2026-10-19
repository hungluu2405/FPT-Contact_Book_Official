package vn.edu.fpt.contactbook.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelUtilsTest {

    private XSSFWorkbook workbook;
    private Row row;
    private int nextColumn;

    @BeforeEach
    void setUp() {
        workbook = new XSSFWorkbook();
        row = workbook.createSheet().createRow(0);
    }

    @AfterEach
    void tearDown() throws IOException {
        workbook.close();
    }

    @Test
    @DisplayName("Số điện thoại ô Text giữ nguyên số 0 đầu")
    void phoneInTextCell() {
        assertThat(ExcelUtils.phone(text("0900000001"))).isEqualTo("0900000001");
        assertThat(ExcelUtils.phone(text(" +84 900 000 001 "))).isEqualTo("+84 900 000 001");
    }

    @Test
    @DisplayName("Số điện thoại ô Numeric: Excel đã bỏ số 0 đầu thì thêm lại, đủ 10 chữ số")
    void phoneInNumericCell() {
        assertThat(ExcelUtils.phone(number(900000001))).isEqualTo("0900000001");
        assertThat(ExcelUtils.phone(number(84900000001L))).isEqualTo("84900000001");
        assertThat(ExcelUtils.phone(number(12345))).isEqualTo("12345");
        assertThat(PhoneUtils.normalize(ExcelUtils.phone(number(84900000001L)))).contains("0900000001");
    }

    @Test
    @DisplayName("Ngày ở ô kiểu ngày và ô chữ dd/MM/yyyy")
    void dateInBothForms() {
        Cell dateCell = row.createCell(nextColumn++);
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
        dateCell.setCellValue(LocalDate.of(2006, 9, 1));
        dateCell.setCellStyle(style);

        assertThat(ExcelUtils.date(dateCell)).isEqualTo(LocalDate.of(2006, 9, 1));
        assertThat(ExcelUtils.date(text("01/09/2006"))).isEqualTo(LocalDate.of(2006, 9, 1));
        assertThat(ExcelUtils.date(text("1/9/2006"))).isEqualTo(LocalDate.of(2006, 9, 1));
        assertThat(ExcelUtils.date(text("  "))).isNull();
        assertThat(ExcelUtils.date(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2006-09-01", "31/02/2006", "01/13/2006", "hôm qua"})
    @DisplayName("Ngày sai dạng hoặc không tồn tại bị từ chối")
    void invalidDates(String value) {
        assertThatThrownBy(() -> ExcelUtils.date(text(value))).isInstanceOf(DateTimeException.class);
    }

    @Test
    @DisplayName("Ô số không định dạng ngày không được coi là ngày")
    void plainNumberIsNotADate() {
        assertThatThrownBy(() -> ExcelUtils.date(number(20060901))).isInstanceOf(DateTimeException.class);
    }

    @Test
    @DisplayName("Dòng trống: không tồn tại, chỉ có khoảng trắng; dòng có một ô có chữ thì không trống")
    void blankRows() {
        assertThat(ExcelUtils.isBlankRow(null, 3)).isTrue();
        text("   ");
        assertThat(ExcelUtils.isBlankRow(row, 3)).isTrue();
        text("x");
        assertThat(ExcelUtils.isBlankRow(row, 3)).isFalse();
        assertThat(ExcelUtils.isBlankRow(row, 1)).as("chỉ xét các cột của loại file").isTrue();
    }

    @Test
    @DisplayName("Chữ được chuẩn hóa Unicode NFC (Nữ tổ hợp = Nữ dựng sẵn)")
    void textIsNfcNormalized() {
        String decomposed = "Nữ";
        assertThat(ExcelUtils.text(text(decomposed))).isEqualTo("Nữ");
    }

    @Test
    @DisplayName("Số thập phân: ô số đọc đúng giá trị dù định dạng ô làm tròn, ô chữ nhận dấu chấm và dấu phẩy")
    void decimalCells() {
        Cell numeric = row.createCell(nextColumn++);
        numeric.setCellValue(8.25);
        CellStyle noDecimals = workbook.createCellStyle();
        noDecimals.setDataFormat(workbook.createDataFormat().getFormat("0"));
        numeric.setCellStyle(noDecimals);

        assertThat(ExcelUtils.decimal(numeric)).isEqualByComparingTo("8.25");
        assertThat(ExcelUtils.decimal(text("7,5"))).isEqualByComparingTo("7.5");
        assertThat(ExcelUtils.decimal(text(" 9.75 "))).isEqualByComparingTo("9.75");
        assertThat(ExcelUtils.decimal(null)).isNull();
        assertThat(ExcelUtils.decimal(row.createCell(nextColumn++))).isNull();
        assertThat(ExcelUtils.decimal(text("   "))).isNull();
    }

    @Test
    @DisplayName("Số thập phân: chữ không phải số hoặc ô ngày bị từ chối")
    void decimalRejectsNonNumbers() {
        assertThatThrownBy(() -> ExcelUtils.decimal(text("tám"))).isInstanceOf(NumberFormatException.class);
        Cell date = row.createCell(nextColumn++);
        date.setCellValue(LocalDate.of(2026, 9, 1));
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
        date.setCellStyle(dateStyle);
        assertThatThrownBy(() -> ExcelUtils.decimal(date)).isInstanceOf(NumberFormatException.class);
    }

    private Cell text(String value) {
        Cell cell = row.createCell(nextColumn++);
        cell.setCellValue(value);
        return cell;
    }

    private Cell number(long value) {
        Cell cell = row.createCell(nextColumn++);
        cell.setCellValue(value);
        return cell;
    }
}
