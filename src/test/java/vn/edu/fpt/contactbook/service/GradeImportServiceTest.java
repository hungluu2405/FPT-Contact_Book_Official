package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.service.GradeImportService.ParsedCell;
import vn.edu.fpt.contactbook.service.GradeImportService.ParsedRow;
import vn.edu.fpt.contactbook.service.GradeService.CellUpdate;
import vn.edu.fpt.contactbook.service.GradeService.Loaded;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Kiểm tra file nhập điểm: lỗi theo dòng và theo ô, ô trống là không thay đổi, có giá trị ở ô đã công bố là lỗi.
 */
class GradeImportServiceTest {

    private Loaded data;
    private Enrollment e1;
    private final Map<String, Grade> grades = new HashMap<>();

    @BeforeEach
    void setUp() {
        Department se = CatalogTestData.department(1L, "SE");
        ClassSection section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10",
                se), CatalogTestData.semester(1L, "HK1-2627"), CatalogTestData.teacher(2L, "GV002", se), 40,
                SectionStatus.OPEN);
        e1 = CatalogTestData.enrollment(100L, section, CatalogTestData.student(1L, "HS260001",
                StudentStatus.STUDYING), EnrollmentStatus.ENROLLED);
        Enrollment e2 = CatalogTestData.enrollment(101L, section, CatalogTestData.student(2L, "HS260002",
                StudentStatus.STUDYING), EnrollmentStatus.ENROLLED);
        List<GradeComponent> components = List.of(GradeRuleTest.component(1L, "40", "10"),
                GradeRuleTest.component(2L, "60", "5"));
        data = new Loaded(section, components, List.of(e1, e2), grades);
    }

    @Test
    @DisplayName("File hợp lệ: chỉ ô có giá trị được ghi, ô trống giữ nguyên")
    void validFile() {
        List<CellUpdate> updates = new ArrayList<>();

        List<ImportRowError> errors = GradeImportService.validate(List.of(
                row(2, "HS260001", cell("8.5"), blank()),
                row(3, "HS260002", blank(), cell("4"))), data, updates);

        assertThat(errors).isEmpty();
        assertThat(updates).extracting(u -> u.enrollment().getId(), u -> u.component().getId(), CellUpdate::score)
                .containsExactly(tuple(100L, 1L, new BigDecimal("8.5")), tuple(101L, 2L, new BigDecimal("4")));
    }

    @Test
    @DisplayName("Lỗi theo dòng: thiếu mã, trùng dòng, sinh viên không thuộc lớp")
    void rowErrors() {
        List<ImportRowError> errors = GradeImportService.validate(List.of(
                row(2, null, cell("8"), blank()),
                row(3, "HS260001", cell("8"), blank()),
                row(4, "HS260001", cell("9"), blank()),
                row(5, "SE999999", cell("9"), blank())), data, new ArrayList<>());

        assertThat(errors).extracting(ImportRowError::rowNumber, ImportRowError::column)
                .containsExactly(tuple(2, "Mã SV"), tuple(4, "Mã SV"), tuple(5, "Mã SV"));
        assertThat(errors.get(1).message()).isEqualTo("Trùng với dòng 3 trong file");
    }

    @Test
    @DisplayName("Lỗi theo ô: không phải số, vượt max_score của đầu điểm (max 5), quá 2 chữ số thập phân")
    void cellErrors() {
        List<ImportRowError> errors = GradeImportService.validate(List.of(
                row(2, "HS260001", invalid(), cell("5.5")),
                row(3, "HS260002", cell("8.125"), blank())), data, new ArrayList<>());

        assertThat(errors).extracting(ImportRowError::rowNumber, ImportRowError::column, ImportRowError::message)
                .containsExactly(tuple(2, "Đầu điểm 1", GradeMessages.SCORE_INVALID),
                        tuple(2, "Đầu điểm 2", "Điểm phải từ 0 đến 5"),
                        tuple(3, "Đầu điểm 1", GradeMessages.SCORE_SCALE));
    }

    @Test
    @DisplayName("Có giá trị ở ô đã công bố là lỗi; để trống ô đó thì hợp lệ")
    void publishedCell() {
        Grade published = new Grade();
        published.setPublished(true);
        grades.put(GradeSheet.key(100L, 1L), published);

        List<ImportRowError> errors = GradeImportService.validate(List.of(row(2, "HS260001", cell("9"), blank())),
                data, new ArrayList<>());
        List<ImportRowError> blankOk = GradeImportService.validate(List.of(row(2, "HS260001", blank(), cell("3"))),
                data, new ArrayList<>());

        assertThat(errors).singleElement().satisfies(e -> assertThat(e.message()).contains("đã công bố"));
        assertThat(blankOk).isEmpty();
    }

    private static ParsedRow row(int number, String code, ParsedCell... cells) {
        return new ParsedRow(number, code, List.of(cells));
    }

    private static ParsedCell cell(String value) {
        return new ParsedCell(new BigDecimal(value), false);
    }

    private static ParsedCell blank() {
        return new ParsedCell(null, false);
    }

    private static ParsedCell invalid() {
        return new ParsedCell(null, true);
    }
}
