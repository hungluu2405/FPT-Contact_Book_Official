package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AdminHome;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.NotificationListItem;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.ParentHome;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;
import vn.edu.fpt.contactbook.dto.StudentHome;
import vn.edu.fpt.contactbook.dto.TeacherHome;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.List;
import java.util.function.Supplier;

/**
 * Dữ liệu trang chủ theo vai trò. Chỉ ghép các service sẵn có (phạm vi dữ liệu đã kiểm tra ở đó): giáo viên chỉ lớp
 * mình dạy, học sinh chỉ của mình, phụ huynh chỉ con đã liên kết. Chưa có học kỳ nào thì các khối theo học kỳ trống.
 * Số đơn chờ không tính ở đây: MenuBadgeInterceptor đã đếm cho menu, template dùng lại (mỗi số đếm một lần).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeDashboardService {

    static final int NOTIFICATION_LIMIT = 3;
    static final int EXAM_LIMIT = 3;

    private final ScheduleViewService scheduleViewService;
    private final AttendanceViewService attendanceViewService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ClassSectionRepository classSectionRepository;

    public TeacherHome teacher(AppUserPrincipal actor) {
        SemesterView semester = scheduleViewService.currentSemester().orElse(null);
        long sectionCount = orElse(() -> attendanceViewService.teacherSections(actor, null, 0).data()
                .getTotalElements(), 0L);
        return new TeacherHome(semester, today(actor, Audience.TEACHER, null), sectionCount,
                latestNotifications(actor));
    }

    public StudentHome student(AppUserPrincipal actor) {
        return new StudentHome(scheduleViewService.currentSemester().orElse(null),
                today(actor, Audience.STUDENT, null), upcomingExams(actor, Audience.STUDENT, null),
                attendanceWarnings(actor, Audience.STUDENT, null), latestNotifications(actor));
    }

    public ParentHome parent(AppUserPrincipal actor) {
        List<ParentHome.Child> children = scheduleViewService.children(actor).stream()
                .map(child -> child(actor, child))
                .toList();
        return new ParentHome(scheduleViewService.currentSemester().orElse(null), children,
                latestNotifications(actor));
    }

    public AdminHome admin(AppUserPrincipal actor) {
        SemesterView semester = scheduleViewService.currentSemester().orElse(null);
        long sectionCount = semester == null ? 0 : classSectionRepository.countBySemesterId(semester.id());
        return new AdminHome(semester, userRepository.countByActiveTrue(), studentRepository.count(), sectionCount,
                latestNotifications(actor));
    }

    private ParentHome.Child child(AppUserPrincipal actor, OptionView child) {
        return new ParentHome.Child(child.id(), child.code(), child.name(), today(actor, Audience.PARENT, child.id()),
                upcomingExams(actor, Audience.PARENT, child.id()),
                attendanceWarnings(actor, Audience.PARENT, child.id()));
    }

    /** Các buổi của hôm nay (giờ Asia/Ho_Chi_Minh), theo giờ bắt đầu. */
    private List<ScheduleItem> today(AppUserPrincipal actor, Audience audience, Long studentId) {
        return orElse(() -> scheduleViewService.week(actor, audience, studentId, null, AppTime.today()).data()
                .days().stream()
                .filter(ScheduleWeek.Day::today)
                .flatMap(day -> day.items().stream())
                .toList(), List.of());
    }

    private List<ExamView> upcomingExams(AppUserPrincipal actor, Audience audience, Long studentId) {
        return orElse(() -> scheduleViewService.upcomingExams(actor, audience, studentId, EXAM_LIMIT), List.of());
    }

    /** Các môn đang vượt ngưỡng vắng (cùng quy tắc với trang điểm danh). */
    private List<StudentAttendanceRow> attendanceWarnings(AppUserPrincipal actor, Audience audience,
                                                          Long studentId) {
        return orElse(() -> attendanceViewService.student(actor, audience, studentId, null).data().stream()
                .filter(row -> row.summary().warning())
                .toList(), List.of());
    }

    private List<NotificationListItem> latestNotifications(AppUserPrincipal actor) {
        return notificationService.inbox(actor, 0).getContent().stream().limit(NOTIFICATION_LIMIT).toList();
    }

    /** Chưa có học kỳ nào (NotFoundException) thì dùng giá trị rỗng thay vì báo lỗi trên trang chủ. */
    private static <T> T orElse(Supplier<T> query, T empty) {
        try {
            return query.get();
        } catch (NotFoundException e) {
            return empty;
        }
    }
}
