package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.fpt.contactbook.dto.AttendanceReport;
import vn.edu.fpt.contactbook.dto.GradeReport;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.dto.SectionAttendanceRow;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Số liệu báo cáo phải khớp các màn hình đã có (cùng công thức, không có công thức thứ hai): báo cáo điểm danh một lớp
 * = bảng thống kê điểm danh của lớp (AttendanceViewService.section); trung bình môn trong báo cáo điểm = bảng điểm
 * (GradeService.sheet). Dữ liệu mẫu lớp 27 TIN11-HK1-2627-01: 2 SV ENROLLED (enrollments 17, 18), có điểm danh; đầu
 * điểm 10..14 của TIN11 (đủ 100%), mỗi SV mới có điểm đầu điểm 10 và 14.
 */
@IntegrationTest
class ReportConsistencyIntegrationTest {

    static final long SECTION = 27L;

    @Autowired AttendanceReportService attendanceReportService;
    @Autowired AttendanceViewService attendanceViewService;
    @Autowired GradeReportService gradeReportService;
    @Autowired GradeService gradeService;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired GradeRepository gradeRepository;
    @Autowired GradeComponentRepository gradeComponentRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("Điểm danh một lớp: từng học sinh (số buổi, chuyên cần, tỷ lệ vắng, cảnh báo) giống hệt trang thống kê")
    void attendanceMatchesSectionStatistics() {
        AppUserPrincipal head = principal(TestAccounts.TEACHER_HEAD);

        AttendanceReport report = attendanceReportService.report(head, new ReportFilter(null, null, SECTION), 0);
        List<SectionAttendanceRow> screen = attendanceViewService.section(head, Access.MANAGER, SECTION, 0, 0)
                .students().getContent();

        assertThat(report.section().students()).isNotEmpty().containsExactlyElementsOf(screen);
        assertThat(report.section().totalSessions())
                .isEqualTo(attendanceViewService.section(head, Access.MANAGER, SECTION, 0, 0).totalSessions());
        assertThat(report.section().warnings()).isEqualTo(screen.stream().filter(r -> r.summary().warning()).count());
        // Dòng của lớp trong bảng tổng hợp học kỳ dùng cùng số liệu.
        assertThat(attendanceReportService.report(head, new ReportFilter(1L, null, null), 0).rows().getContent())
                .filteredOn(r -> r.section().id() == SECTION).singleElement()
                .satisfies(row -> {
                    assertThat(row.students()).isEqualTo(screen.size());
                    assertThat(row.attendanceRate()).isEqualTo(report.section().classSummary().attendanceRate());
                });
    }

    @Test
    @DisplayName("Điểm một lớp: trung bình môn từng học sinh giống bảng điểm, kể cả khi còn thiếu điểm")
    void gradeAveragesMatchGradeSheet() {
        AppUserPrincipal head = principal(TestAccounts.TEACHER_HEAD);
        // Cho enrollment 17 đủ điểm (thêm đầu điểm 11, 12, 13, chưa công bố); enrollment 18 vẫn thiếu.
        addGrade(17L, 11L, "7.00");
        addGrade(17L, 12L, "9.00");
        addGrade(17L, 13L, "6.00");

        GradeReport report = gradeReportService.report(head, new ReportFilter(null, null, SECTION), 0);
        GradeSheet sheet = gradeService.sheet(head, Access.MANAGER, SECTION);

        Map<String, BigDecimal> screen = new HashMap<>();
        sheet.rows().forEach(row -> screen.put(row.studentCode(), row.average()));
        assertThat(report.section().students()).hasSize(sheet.rows().size()).allSatisfy(row ->
                assertThat(row.average()).isEqualTo(screen.get(row.studentCode())));
        assertThat(report.section().students()).filteredOn(r -> r.average() != null).hasSize(1);
        assertThat(report.stats().incomplete()).isEqualTo(1);
        assertThat(report.stats().complete()).isEqualTo(1);
        // Điểm chưa công bố vẫn được tính (chỉ GV/TBM/ADMIN xem báo cáo).
        assertThat(report.stats().mean()).isNotNull();
    }

    private void addGrade(long enrollmentId, long componentId, String score) {
        Grade grade = new Grade();
        grade.setEnrollment(enrollmentRepository.findById(enrollmentId).orElseThrow());
        grade.setComponent(gradeComponentRepository.findById(componentId).orElseThrow());
        grade.setScore(new BigDecimal(score));
        grade.setGradedBy(userRepository.getReferenceById(16L));
        grade.setGradedAt(AppTime.now());
        gradeRepository.saveAndFlush(grade);
    }

    private AppUserPrincipal principal(String phone) {
        return (AppUserPrincipal) userDetailsService.loadUserByUsername(phone);
    }
}
