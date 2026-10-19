package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.GradeReport;
import vn.edu.fpt.contactbook.dto.GradeReport.Mode;
import vn.edu.fpt.contactbook.dto.GradeReport.SectionDetail;
import vn.edu.fpt.contactbook.dto.GradeReport.SectionRow;
import vn.edu.fpt.contactbook.dto.GradeReport.StudentRow;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.dto.GradeStats;
import vn.edu.fpt.contactbook.dto.ReportFile;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ReportScope.Context;
import vn.edu.fpt.contactbook.util.AppTime;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Báo cáo điểm (SRS FR-REPORT-02) theo lớp học phần và theo môn: điểm trung bình, phổ điểm, tỷ lệ đạt (ReportRule).
 * Trung bình môn của từng sinh viên tính bằng GradeRule.average trên MỌI điểm đã nhập (kể cả chưa công bố, vì chỉ
 * GV/TBM/ADMIN xem), cùng tập sinh viên ENROLLED với bảng điểm của lớp. Phạm vi kiểm tra ở ReportScope.
 */
@Service
@RequiredArgsConstructor
public class GradeReportService {

    static final String REPORT_TYPE = "GRADES";
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReportScope reportScope;
    private final EnrollmentRepository enrollmentRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final GradeRepository gradeRepository;
    private final AuditService auditService;

    /** Điểm của một lớp: đầu điểm của môn, từng sinh viên ENROLLED với trung bình môn (null là chưa đủ điểm). */
    private record SectionData(ClassSection section, List<GradeComponent> components, List<StudentRow> students) {

        List<BigDecimal> averages() {
            return students.stream().map(StudentRow::average).toList();
        }

        SectionRow toRow() {
            return new SectionRow(AttendanceViewService.toSectionView(section),
                    ReportExports.teacherName(section), GradeRule.weightsComplete(components),
                    ReportRule.gradeStats(averages()));
        }
    }

    @Transactional(readOnly = true)
    public GradeReport report(AppUserPrincipal actor, ReportFilter filter, int page) {
        Context context = reportScope.resolve(actor, filter);
        Mode mode = mode(context);
        if (mode == Mode.SECTION) {
            SectionData data = load(context.sections()).getFirst();
            return new GradeReport(context.selection(), mode, toDetail(data), ReportRule.gradeStats(data.averages()),
                    Page.empty(CatalogSupport.page(page)));
        }
        Pageable pageable = CatalogSupport.page(page);
        List<ClassSection> sections = context.sections();
        int from = (int) Math.min(pageable.getOffset(), sections.size());
        List<ClassSection> pageSections = sections.subList(from, Math.min(sections.size(),
                from + pageable.getPageSize()));
        // Theo môn: thống kê gộp cần MỌI lớp của môn; tổng quan chỉ cần các lớp của trang đang xem.
        List<SectionData> data = load(mode == Mode.SUBJECT ? sections : pageSections);
        Map<Long, SectionRow> rows = data.stream().collect(Collectors.toMap(d -> d.section().getId(),
                SectionData::toRow));
        Page<SectionRow> pageRows = new PageImpl<>(pageSections.stream().map(s -> rows.get(s.getId())).toList(),
                pageable, sections.size());
        GradeStats stats = mode == Mode.SUBJECT ? ReportRule.gradeStats(data.stream()
                .flatMap(d -> d.averages().stream()).toList()) : null;
        return new GradeReport(context.selection(), mode, null, stats, pageRows);
    }

    /** Xuất đúng bộ lọc đang chọn (mọi dòng, không phân trang) và ghi audit EXPORT_REPORT. */
    @Transactional
    public ReportFile export(AppUserPrincipal actor, ReportFilter filter, String ipAddress) {
        Context context = reportScope.resolve(actor, filter);
        Mode mode = mode(context);
        List<SectionData> data = load(context.sections());
        List<String> titles = new ArrayList<>(ReportExports.titles(ReportMessages.GRADE_TITLE, context));
        titles.add(ReportMessages.EXPORTED_AT + AppTime.now().format(EXPORTED_AT));

        List<ReportWorkbooks.SheetData> sheets = new ArrayList<>();
        int rowCount;
        if (mode == Mode.SECTION) {
            SectionData section = data.getFirst();
            List<List<Object>> rows = studentRows(section);
            rowCount = rows.size();
            sheets.add(new ReportWorkbooks.SheetData(ReportMessages.SHEET_STUDENTS, titles,
                    studentHeaders(section.components()), rows));
            sheets.add(statsSheet(titles, ReportRule.gradeStats(section.averages())));
        } else {
            List<List<Object>> rows = new ArrayList<>(data.stream().map(d -> sectionCells(d.toRow())).toList());
            rowCount = rows.size();
            if (mode == Mode.SUBJECT) {
                GradeStats stats = ReportRule.gradeStats(data.stream().flatMap(d -> d.averages().stream()).toList());
                rows.add(statsCells(ReportMessages.WHOLE_SUBJECT, context.subject().getCode(),
                        context.subject().getName(), null, stats));
                sheets.add(new ReportWorkbooks.SheetData(ReportMessages.SHEET_SECTIONS, titles, sectionHeaders(),
                        rows));
                sheets.add(statsSheet(titles, stats));
            } else {
                sheets.add(new ReportWorkbooks.SheetData(ReportMessages.SHEET_SECTIONS, titles, sectionHeaders(),
                        rows));
            }
        }
        byte[] content = ReportWorkbooks.write(sheets);
        ReportExports.audit(auditService, actor, REPORT_TYPE, context, rowCount, ipAddress);
        return new ReportFile(ReportExports.fileName("bao-cao-diem", context), content);
    }

    private static Mode mode(Context context) {
        return context.section() != null ? Mode.SECTION : context.subject() != null ? Mode.SUBJECT : Mode.OVERVIEW;
    }

    private List<SectionData> load(List<ClassSection> sections) {
        if (sections.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sections.stream().map(ClassSection::getId).toList();
        Map<Long, List<GradeComponent>> componentsBySubject = IdChunks.query(
                        sections.stream().map(s -> s.getSubject().getId()).distinct().toList(),
                        gradeComponentRepository::findBySubjectIdInOrderByIdAsc)
                .stream().collect(Collectors.groupingBy(c -> c.getSubject().getId()));
        Map<Long, List<Enrollment>> enrolled = IdChunks.query(ids,
                        chunk -> enrollmentRepository.findBySectionsAndStatus(chunk, EnrollmentStatus.ENROLLED))
                .stream().collect(Collectors.groupingBy(e -> e.getClassSection().getId()));
        // enrollmentId → componentId → điểm (ô có dòng nhưng score NULL là chưa có điểm, như bảng điểm).
        Map<Long, Map<Long, BigDecimal>> scores = new HashMap<>();
        for (Grade grade : IdChunks.query(ids, gradeRepository::findBySectionIds)) {
            scores.computeIfAbsent(grade.getEnrollment().getId(), key -> new HashMap<>())
                    .put(grade.getComponent().getId(), grade.getScore());
        }

        List<SectionData> result = new ArrayList<>();
        for (ClassSection section : sections) {
            List<GradeComponent> components = componentsBySubject.getOrDefault(section.getSubject().getId(),
                    List.of());
            List<StudentRow> students = enrolled.getOrDefault(section.getId(), List.of()).stream().map(e -> {
                Map<Long, BigDecimal> mine = scores.getOrDefault(e.getId(), Map.of());
                return new StudentRow(e.getStudent().getStudentCode(), e.getStudent().getUser().getFullName(),
                        components.stream().map(c -> mine.get(c.getId())).toList(),
                        GradeRule.average(components, mine));
            }).toList();
            result.add(new SectionData(section, components, students));
        }
        return result;
    }

    private static SectionDetail toDetail(SectionData data) {
        return new SectionDetail(AttendanceViewService.toSectionView(data.section()),
                ReportExports.teacherName(data.section()),
                data.components().stream().map(c -> new GradeSheet.Component(c.getId(), c.getName(), c.getWeight(),
                        c.getMaxScore())).toList(),
                GradeRule.weightsComplete(data.components()), data.students());
    }

    private static List<String> studentHeaders(List<GradeComponent> components) {
        List<String> headers = new ArrayList<>(List.of(ReportMessages.STUDENT_CODE, ReportMessages.FULL_NAME));
        components.forEach(c -> headers.add(c.getName() + " (" + c.getWeight().stripTrailingZeros().toPlainString()
                + "%)"));
        headers.add(ReportMessages.AVERAGE);
        return headers;
    }

    private static List<List<Object>> studentRows(SectionData data) {
        List<List<Object>> rows = new ArrayList<>();
        for (StudentRow student : data.students()) {
            List<Object> cells = new ArrayList<>(List.of(student.studentCode(), student.fullName()));
            cells.addAll(student.scores());
            cells.add(student.average() == null ? ReportMessages.INCOMPLETE : student.average());
            rows.add(cells);
        }
        return rows;
    }

    private static List<String> sectionHeaders() {
        List<String> headers = new ArrayList<>(ReportMessages.GRADE_SECTION_HEADERS);
        GradeStats.BANDS.forEach(band -> headers.add(ReportMessages.STATS_BAND + band));
        headers.add(ReportMessages.WEIGHTS_INCOMPLETE);
        return headers;
    }

    private static List<Object> sectionCells(SectionRow row) {
        List<Object> cells = statsCells(row.section().code(), row.section().subjectCode(),
                row.section().subjectName(), row.teacherName(), row.stats());
        cells.add(row.weightsComplete() ? null : ReportMessages.YES);
        return cells;
    }

    private static List<Object> statsCells(String section, String subjectCode, String subjectName, String teacher,
                                           GradeStats stats) {
        List<Object> cells = new ArrayList<>(ReportExports.cells(section, subjectCode, subjectName, teacher,
                stats.students(), stats.complete(), stats.incomplete(), stats.mean(), stats.passed(),
                stats.passRate()));
        cells.addAll(stats.distribution());
        return cells;
    }

    private static ReportWorkbooks.SheetData statsSheet(List<String> titles, GradeStats stats) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(ReportExports.cells(ReportMessages.STATS_STUDENTS, stats.students()));
        rows.add(ReportExports.cells(ReportMessages.STATS_COMPLETE, stats.complete()));
        rows.add(ReportExports.cells(ReportMessages.STATS_INCOMPLETE, stats.incomplete()));
        rows.add(ReportExports.cells(ReportMessages.STATS_MEAN, stats.mean()));
        rows.add(ReportExports.cells(ReportMessages.STATS_PASSED, stats.passed()));
        rows.add(ReportExports.cells(ReportMessages.STATS_PASS_RATE, stats.passRate()));
        for (int i = 0; i < GradeStats.BANDS.size(); i++) {
            rows.add(ReportExports.cells(ReportMessages.STATS_BAND + GradeStats.BANDS.get(i),
                    stats.distribution().get(i)));
        }
        return new ReportWorkbooks.SheetData(ReportMessages.SHEET_STATS, titles, ReportMessages.GRADE_STATS_HEADERS,
                rows);
    }
}
