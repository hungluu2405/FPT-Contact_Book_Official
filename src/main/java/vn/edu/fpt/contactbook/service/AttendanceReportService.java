package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttendanceReport;
import vn.edu.fpt.contactbook.dto.AttendanceReport.SectionDetail;
import vn.edu.fpt.contactbook.dto.AttendanceReport.SectionRow;
import vn.edu.fpt.contactbook.dto.AttendanceReport.Totals;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.dto.ReportFile;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.dto.SectionAttendanceRow;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ReportScope.Context;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Báo cáo điểm danh (SRS FR-REPORT-01): theo lớp học phần (từng sinh viên) và tổng hợp theo học kỳ. Phạm vi kiểm
 * tra ở ReportScope. Mọi tỷ lệ tính bằng AttendanceRule (cùng công thức, cùng mẫu số và cùng tập sinh viên ENROLLED
 * với trang thống kê điểm danh của lớp); chuyên cần của cả lớp là AttendanceRule áp lên tổng số buổi theo trạng
 * thái của các sinh viên trong lớp.
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    static final String REPORT_TYPE = "ATTENDANCE";
    private static final DateTimeFormatter EXPORTED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReportScope reportScope;
    private final AttendanceRule rule;
    private final ClassSessionRepository classSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AuditService auditService;

    /** Số liệu của một lớp: sinh viên ENROLLED kèm tổng hợp, và tổng số buổi theo trạng thái của cả lớp. */
    private record SectionData(ClassSection section, long totalSessions, List<SectionAttendanceRow> students,
                               Map<AttendanceStatus, Long> classCounts) {

        long warnings() {
            return students.stream().filter(s -> s.summary().warning()).count();
        }
    }

    @Transactional(readOnly = true)
    public AttendanceReport report(AppUserPrincipal actor, ReportFilter filter, int page) {
        Context context = reportScope.resolve(actor, filter);
        List<SectionData> data = load(context.sections());
        Pageable pageable = CatalogSupport.page(page);
        List<SectionRow> rows = data.stream().map(this::toRow).toList();
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        Page<SectionRow> pageRows = new PageImpl<>(rows.subList(from, Math.min(rows.size(),
                from + pageable.getPageSize())), pageable, rows.size());
        SectionDetail detail = context.section() == null ? null : toDetail(data.getFirst());
        return new AttendanceReport(context.selection(), rule.thresholdPercent(), detail, pageRows, totals(data));
    }

    /** Xuất đúng bộ lọc đang chọn (mọi dòng, không phân trang) và ghi audit EXPORT_REPORT. */
    @Transactional
    public ReportFile export(AppUserPrincipal actor, ReportFilter filter, String ipAddress) {
        Context context = reportScope.resolve(actor, filter);
        List<SectionData> data = load(context.sections());
        List<String> titles = titles(context);
        ReportWorkbooks.SheetData sheet = context.section() == null
                ? new ReportWorkbooks.SheetData(ReportMessages.SHEET_SECTIONS, titles,
                ReportMessages.ATTENDANCE_SECTION_HEADERS, sectionRows(data))
                : new ReportWorkbooks.SheetData(ReportMessages.SHEET_STUDENTS, titles,
                ReportMessages.ATTENDANCE_STUDENT_HEADERS, studentRows(data.getFirst()));
        byte[] content = ReportWorkbooks.write(List.of(sheet));
        ReportExports.audit(auditService, actor, REPORT_TYPE, context, sheet.rows().size(), ipAddress);
        return new ReportFile(ReportExports.fileName("bao-cao-diem-danh", context), content);
    }

    private List<SectionData> load(List<ClassSection> sections) {
        if (sections.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sections.stream().map(ClassSection::getId).toList();
        Map<Long, Long> totals = IdChunks.query(ids, classSessionRepository::countActiveBySections).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, List<Enrollment>> enrolled = IdChunks.query(ids,
                        chunk -> enrollmentRepository.findBySectionsAndStatus(chunk, EnrollmentStatus.ENROLLED))
                .stream().collect(Collectors.groupingBy(e -> e.getClassSection().getId()));
        // classSectionId → studentId → (trạng thái → số buổi)
        Map<Long, Map<Long, Map<AttendanceStatus, Long>>> counts = new HashMap<>();
        for (Object[] row : IdChunks.query(ids, attendanceRepository::countBySectionStudentAndStatus)) {
            counts.computeIfAbsent((Long) row[0], key -> new HashMap<>())
                    .computeIfAbsent((Long) row[1], key -> new EnumMap<>(AttendanceStatus.class))
                    .put((AttendanceStatus) row[2], (Long) row[3]);
        }

        List<SectionData> result = new ArrayList<>();
        for (ClassSection section : sections) {
            long total = totals.getOrDefault(section.getId(), 0L);
            Map<Long, Map<AttendanceStatus, Long>> bySection = counts.getOrDefault(section.getId(), Map.of());
            List<Map<AttendanceStatus, Long>> studentCounts = new ArrayList<>();
            List<SectionAttendanceRow> students = new ArrayList<>();
            for (Enrollment enrollment : enrolled.getOrDefault(section.getId(), List.of())) {
                Student student = enrollment.getStudent();
                Map<AttendanceStatus, Long> mine = bySection.getOrDefault(student.getId(), Map.of());
                studentCounts.add(mine);
                students.add(new SectionAttendanceRow(student.getId(), student.getStudentCode(),
                        student.getUser().getFullName(), rule.summarize(mine, total)));
            }
            result.add(new SectionData(section, total, students, ReportRule.sum(studentCounts)));
        }
        return result;
    }

    private SectionRow toRow(SectionData data) {
        return new SectionRow(AttendanceViewService.toSectionView(data.section()),
                ReportExports.teacherName(data.section()), data.students().size(), data.totalSessions(),
                rule.summarize(data.classCounts(), data.totalSessions()).attendanceRate(), data.warnings());
    }

    private SectionDetail toDetail(SectionData data) {
        return new SectionDetail(AttendanceViewService.toSectionView(data.section()),
                ReportExports.teacherName(data.section()), data.totalSessions(), data.students(), data.warnings(),
                rule.summarize(data.classCounts(), data.totalSessions()));
    }

    private Totals totals(List<SectionData> data) {
        Map<AttendanceStatus, Long> all = ReportRule.sum(data.stream().map(SectionData::classCounts).toList());
        long students = data.stream().mapToLong(d -> d.students().size()).sum();
        long warnings = data.stream().mapToLong(SectionData::warnings).sum();
        // Chỉ dùng tỷ lệ chuyên cần (mẫu số là số buổi đã điểm danh), nên số buổi của lớp không ảnh hưởng.
        return new Totals(data.size(), students, warnings, rule.summarize(all, 0).attendanceRate());
    }

    private List<String> titles(Context context) {
        List<String> titles = new ArrayList<>(ReportExports.titles(ReportMessages.ATTENDANCE_TITLE, context));
        titles.add(ReportMessages.THRESHOLD.formatted(rule.thresholdPercent()));
        titles.add(ReportMessages.EXPORTED_AT + AppTime.now().format(EXPORTED_AT));
        return titles;
    }

    private List<List<Object>> sectionRows(List<SectionData> data) {
        List<List<Object>> rows = new ArrayList<>();
        for (SectionData d : data) {
            SectionRow row = toRow(d);
            rows.add(ReportExports.cells(row.section().code(), row.section().subjectCode(),
                    row.section().subjectName(), row.teacherName(), row.students(), row.totalSessions(),
                    row.attendanceRate(), row.warnings()));
        }
        Totals totals = totals(data);
        rows.add(ReportExports.cells(ReportMessages.TOTAL, null, null, null, totals.students(), null,
                totals.attendanceRate(), totals.warnings()));
        return rows;
    }

    private static List<List<Object>> studentRows(SectionData data) {
        List<List<Object>> rows = new ArrayList<>();
        for (SectionAttendanceRow student : data.students()) {
            AttendanceSummary s = student.summary();
            rows.add(ReportExports.cells(student.studentCode(), student.fullName(), s.present(),
                    s.absentExcused(), s.absentUnexcused(), s.markedSessions(), s.totalSessions(),
                    s.attendanceRate(), s.absentRate(), s.warning() ? ReportMessages.YES : null));
        }
        return rows;
    }
}
