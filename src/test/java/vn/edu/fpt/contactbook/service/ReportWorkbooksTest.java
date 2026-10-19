package vn.edu.fpt.contactbook.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ghi file báo cáo: bố cục tiêu đề / tên cột / dữ liệu, kiểu ô, chuỗi giống công thức vẫn là chuỗi, tên file an toàn.
 */
class ReportWorkbooksTest {

    @Test
    @DisplayName("Mở lại được bằng POI: tiêu đề, một dòng trống, tên cột, rồi dữ liệu; số là ô số, null là ô trống")
    void layoutAndTypes() throws Exception {
        byte[] content = ReportWorkbooks.write(List.of(new ReportWorkbooks.SheetData("Sinh viên",
                List.of("Báo cáo điểm danh", "Học kỳ: FA26"), List.of("Mã SV", "Chuyên cần (%)", "Ghi chú"),
                List.of(Arrays.asList("SE190001", new BigDecimal("87.5"), null)))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet("Sinh viên");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Báo cáo điểm danh");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Học kỳ: FA26");
            assertThat(sheet.getRow(2)).isNull();
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Chuyên cần (%)");
            Row data = sheet.getRow(4);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("SE190001");
            assertThat(data.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(1).getNumericCellValue()).isEqualTo(87.5);
            assertThat(data.getCell(2).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    @DisplayName("Nội dung bắt đầu bằng = (tên, mã do người dùng nhập) ghi kiểu chuỗi, không thành công thức")
    void formulaLikeTextStaysText() throws Exception {
        byte[] content = ReportWorkbooks.write(List.of(new ReportWorkbooks.SheetData("S", List.of(),
                List.of("Họ tên"), List.of(List.of("=HYPERLINK(\"http://x\")")))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var cell = workbook.getSheet("S").getRow(2).getCell(0);
            assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
            assertThat(cell.getStringCellValue()).isEqualTo("=HYPERLINK(\"http://x\")");
        }
    }

    @Test
    @DisplayName("Tên file: bỏ phần trống, ký tự ngoài chữ không dấu / số / - / _ đổi thành _")
    void fileName() {
        assertThat(ReportWorkbooks.fileName("bao-cao-diem", "FA26", null, "PRN211_FA26_01"))
                .isEqualTo("bao-cao-diem-FA26-PRN211_FA26_01.xlsx");
        assertThat(ReportWorkbooks.fileName("bao-cao", "Kỳ 1\r\n\"x\"/..")).isEqualTo("bao-cao-K__1___x____.xlsx");
    }
}
