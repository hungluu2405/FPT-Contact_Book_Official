package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import vn.edu.fpt.contactbook.dto.AdminHome;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.NotificationListItem;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.ParentHome;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;
import vn.edu.fpt.contactbook.dto.StudentHome;
import vn.edu.fpt.contactbook.dto.TeacherHome;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.enums.ExamType;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeDashboardServiceTest {

    static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    static final SemesterView SEMESTER = new SemesterView(1L, "HK1-2627", "Học kỳ I", LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 12, 20), true);

    @Mock ScheduleViewService scheduleViewService;
    @Mock AttendanceViewService attendanceViewService;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock StudentRepository studentRepository;
    @Mock ClassSectionRepository classSectionRepository;

    HomeDashboardService service;

    @BeforeEach
    void setUp() {
        service = new HomeDashboardService(scheduleViewService, attendanceViewService, notificationService,
                userRepository, studentRepository, classSectionRepository);
        when(scheduleViewService.currentSemester()).thenReturn(Optional.of(SEMESTER));
        when(notificationService.inbox(any(), eq(0))).thenReturn(new PageImpl<>(List.of(
                notification(1), notification(2), notification(3), notification(4))));
    }

    static AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(7L, "0900000007", "Người dùng", null, true, false, Set.of(role), Map.of());
    }

    static ScheduleItem item(LocalDate date, String subject) {
        return new ScheduleItem(date, LocalTime.of(7, 30), LocalTime.of(9, 0), subject, subject, subject + "-01",
                "R101", "Giáo viên", SessionStatus.SCHEDULED, null);
    }

    /** Tuần có buổi vào thứ Hai và thứ Ba; thứ Ba là hôm nay. */
    static InSemester<ScheduleWeek> week() {
        List<ScheduleWeek.Day> days = List.of(
                new ScheduleWeek.Day(MONDAY, false, List.of(item(MONDAY, "TOAN10"))),
                new ScheduleWeek.Day(MONDAY.plusDays(1), true,
                        List.of(item(MONDAY.plusDays(1), "VAN10"), item(MONDAY.plusDays(1), "ANH10"))));
        return new InSemester<>(SEMESTER, new ScheduleWeek(MONDAY, MONDAY.plusDays(6), MONDAY.minusWeeks(1),
                MONDAY.plusWeeks(1), days));
    }

    static NotificationListItem notification(long id) {
        return new NotificationListItem(id, "Thông báo " + id, "Nhà trường", NotificationPriority.NORMAL,
                LocalDateTime.of(2026, 10, 20, 8, 0), false);
    }

    static StudentAttendanceRow attendance(String subject, boolean warning) {
        return new StudentAttendanceRow(1L, subject + "-01", subject, subject,
                new AttendanceSummary(10, 0, warning ? 6 : 0, 26, new BigDecimal("62.5"),
                        new BigDecimal(warning ? "23.1" : "0.0"), warning));
    }

    static ExamView exam(String subject) {
        return new ExamView(1L, subject + "-01", subject, subject, ExamType.MIDTERM, LocalDate.of(2026, 11, 2),
                LocalTime.of(7, 30), LocalTime.of(9, 0), "R101", null);
    }

    @Test
    @DisplayName("Giáo viên: chỉ buổi của hôm nay, số lớp, 3 thông báo mới nhất")
    void teacherHome() {
        AppUserPrincipal teacher = principal("TEACHER");
        when(scheduleViewService.week(eq(teacher), eq(Audience.TEACHER), isNull(), isNull(), any()))
                .thenReturn(week());
        when(attendanceViewService.teacherSections(teacher, null, 0)).thenReturn(new InSemester<>(SEMESTER,
                new PageImpl<>(List.of(new TeacherSectionView(1L, "VAN10-01", "VAN10", "Ngữ văn 10", "HK1-2627",
                        SectionStatus.OPEN)))));

        TeacherHome home = service.teacher(teacher);

        assertThat(home.semester()).isEqualTo(SEMESTER);
        assertThat(home.today()).extracting(ScheduleItem::subjectCode).containsExactly("VAN10", "ANH10");
        assertThat(home.sectionCount()).isEqualTo(1);
        assertThat(home.notifications()).extracting(NotificationListItem::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Chưa có học kỳ nào: trang chủ giáo viên vẫn mở được, các khối trống")
    void teacherHomeWithoutSemester() {
        AppUserPrincipal teacher = principal("TEACHER");
        when(scheduleViewService.currentSemester()).thenReturn(Optional.empty());
        when(scheduleViewService.week(any(), any(), any(), any(), any()))
                .thenThrow(new NotFoundException("Không tìm thấy học kỳ"));
        when(attendanceViewService.teacherSections(any(), any(), anyInt()))
                .thenThrow(new NotFoundException("Không tìm thấy học kỳ"));

        TeacherHome home = service.teacher(teacher);

        assertThat(home.semester()).isNull();
        assertThat(home.today()).isEmpty();
        assertThat(home.sectionCount()).isZero();
    }

    @Test
    @DisplayName("Học sinh: buổi hôm nay, 3 lịch thi sắp tới, chỉ các môn vượt ngưỡng vắng")
    void studentHome() {
        AppUserPrincipal student = principal("STUDENT");
        when(scheduleViewService.week(eq(student), eq(Audience.STUDENT), isNull(), isNull(), any()))
                .thenReturn(week());
        when(scheduleViewService.upcomingExams(student, Audience.STUDENT, null, 3))
                .thenReturn(List.of(exam("TOAN10"), exam("VAN10")));
        when(attendanceViewService.student(student, Audience.STUDENT, null, null)).thenReturn(new InSemester<>(
                SEMESTER, List.of(attendance("TOAN10", false), attendance("VAN10", true))));

        StudentHome home = service.student(student);

        assertThat(home.today()).hasSize(2);
        assertThat(home.upcomingExams()).extracting(ExamView::subjectCode).containsExactly("TOAN10", "VAN10");
        assertThat(home.attendanceWarnings()).extracting(StudentAttendanceRow::subjectCode).containsExactly("VAN10");
        assertThat(home.notifications()).hasSize(3);
    }

    @Test
    @DisplayName("Phụ huynh: mỗi con một thẻ với dữ liệu của đúng con đó")
    void parentHome() {
        AppUserPrincipal parent = principal("PARENT");
        when(scheduleViewService.children(parent)).thenReturn(List.of(
                new OptionView(26L, "HS250201", "Bùi Diệu Linh", null),
                new OptionView(37L, "HS240301", "Hồ Bảo Long", null)));
        when(scheduleViewService.week(eq(parent), eq(Audience.PARENT), eq(26L), isNull(), any()))
                .thenReturn(week());
        when(scheduleViewService.week(eq(parent), eq(Audience.PARENT), eq(37L), isNull(), any()))
                .thenReturn(new InSemester<>(SEMESTER, new ScheduleWeek(MONDAY, MONDAY.plusDays(6),
                        MONDAY.minusWeeks(1), MONDAY.plusWeeks(1), List.of())));
        when(attendanceViewService.student(parent, Audience.PARENT, 26L, null))
                .thenReturn(new InSemester<>(SEMESTER, List.of(attendance("ANH11", false))));
        when(attendanceViewService.student(parent, Audience.PARENT, 37L, null))
                .thenReturn(new InSemester<>(SEMESTER, List.of(attendance("VAN8", true))));

        ParentHome home = service.parent(parent);

        assertThat(home.semester()).isEqualTo(SEMESTER);
        assertThat(home.children()).extracting(ParentHome.Child::studentCode).containsExactly("HS250201", "HS240301");
        assertThat(home.children().get(0).today()).hasSize(2);
        assertThat(home.children().get(0).attendanceWarnings()).isEmpty();
        assertThat(home.children().get(1).today()).isEmpty();
        assertThat(home.children().get(1).attendanceWarnings()).extracting(StudentAttendanceRow::subjectCode)
                .containsExactly("VAN8");
    }

    @Test
    @DisplayName("Phụ huynh chưa liên kết con nào: danh sách con trống")
    void parentWithoutChildren() {
        AppUserPrincipal parent = principal("PARENT");
        when(scheduleViewService.children(parent)).thenReturn(List.of());

        assertThat(service.parent(parent).children()).isEmpty();
    }

    @Test
    @DisplayName("Quản trị: học kỳ hiện tại, số tài khoản, học sinh, lớp môn học của học kỳ")
    void adminHome() {
        when(scheduleViewService.currentSemester()).thenReturn(Optional.of(SEMESTER));
        when(userRepository.countByActiveTrue()).thenReturn(128L);
        when(studentRepository.count()).thenReturn(48L);
        when(classSectionRepository.countBySemesterId(1L)).thenReturn(42L);

        AdminHome home = service.admin(principal("ADMIN"));

        assertThat(home.semester()).isEqualTo(SEMESTER);
        assertThat(home.activeUsers()).isEqualTo(128);
        assertThat(home.students()).isEqualTo(48);
        assertThat(home.sectionCount()).isEqualTo(42);
        assertThat(home.notifications()).hasSize(3);
    }

    @Test
    @DisplayName("Quản trị khi chưa có học kỳ: không đếm lớp môn học")
    void adminHomeWithoutSemester() {
        when(scheduleViewService.currentSemester()).thenReturn(Optional.empty());

        AdminHome home = service.admin(principal("ADMIN"));

        assertThat(home.semester()).isNull();
        assertThat(home.sectionCount()).isZero();
    }
}
