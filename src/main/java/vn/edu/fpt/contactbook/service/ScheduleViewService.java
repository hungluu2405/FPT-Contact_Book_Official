package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.ScheduleItem;
import vn.edu.fpt.contactbook.dto.ScheduleWeek;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thời khóa biểu và lịch thi để xem (SRS FR-SCHEDULE-03, 04). Phạm vi kiểm tra ở đây, không ở controller:
 * giảng viên chỉ thấy lớp mình dạy; sinh viên chỉ thấy lớp mình đang ENROLLED; phụ huynh chỉ thấy con đã liên
 * kết trong parent_student (con khác → 403). Mặc định là học kỳ hiện tại; tuần bắt đầu từ thứ Hai.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleViewService {

    private final SemesterRepository semesterRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassSectionRepository classSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ExamRepository examRepository;

    /** Ai đang xem: lịch dạy của giảng viên, lịch của sinh viên, hay lịch của con (studentId). */
    public enum Audience { TEACHER, STUDENT, PARENT }

    /**
     * Một tuần của thời khóa biểu. day null: tuần chứa hôm nay nếu hôm nay nằm trong học kỳ, ngược lại tuần đầu
     * tiên của học kỳ.
     */
    public InSemester<ScheduleWeek> week(AppUserPrincipal actor, Audience audience, Long studentId, Long semesterId,
                                         LocalDate day) {
        Semester semester = resolveSemester(semesterId);
        List<Long> sections = sections(actor, audience, studentId, semester);
        LocalDate today = AppTime.today();
        LocalDate anchor = day != null ? day
                : today.isBefore(semester.getStartDate()) || today.isAfter(semester.getEndDate())
                ? semester.getStartDate() : today;
        LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        Map<LocalDate, List<ScheduleItem>> byDate = classSessionRepository.findForSections(sections, monday, sunday)
                .stream().map(ScheduleViewService::toItem)
                .collect(Collectors.groupingBy(ScheduleItem::date));
        List<ScheduleWeek.Day> days = new ArrayList<>();
        for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1)) {
            days.add(new ScheduleWeek.Day(date, date.equals(today), byDate.getOrDefault(date, List.of())));
        }
        return new InSemester<>(toView(semester),
                new ScheduleWeek(monday, sunday, monday.minusWeeks(1), monday.plusWeeks(1), days));
    }

    /** Toàn bộ buổi của học kỳ theo ngày giờ, phân trang (dạng danh sách). */
    public InSemester<Page<ScheduleItem>> sessions(AppUserPrincipal actor, Audience audience, Long studentId,
                                                   Long semesterId, int page) {
        Semester semester = resolveSemester(semesterId);
        return new InSemester<>(toView(semester), classSessionRepository.findPageForSections(
                sections(actor, audience, studentId, semester), CatalogSupport.page(page))
                .map(ScheduleViewService::toItem));
    }

    public InSemester<Page<ExamView>> exams(AppUserPrincipal actor, Audience audience, Long studentId,
                                            Long semesterId, int page) {
        Semester semester = resolveSemester(semesterId);
        return new InSemester<>(toView(semester), examRepository.findPageForSections(
                sections(actor, audience, studentId, semester), CatalogSupport.page(page))
                .map(exam -> ExamService.toView(exam, exam.getClassSection())));
    }

    /** Các con đã liên kết của phụ huynh đang đăng nhập (bộ chọn con): id là students.id. */
    public List<OptionView> children(AppUserPrincipal actor) {
        return parentRepository.findByUserId(actor.getUserId())
                .map(parent -> parentStudentRepository.findByIdParentId(parent.getId()).stream()
                        .map(link -> new OptionView(link.getStudent().getId(), link.getStudent().getStudentCode(),
                                link.getStudent().getUser().getFullName(), null))
                        .toList())
                .orElse(List.of());
    }

    /** Phụ huynh chỉ xem được con đã liên kết; không phải phụ huynh hoặc chưa liên kết → 403. */
    void requireChild(AppUserPrincipal actor, Long studentId) {
        Parent parent = parentRepository.findByUserId(actor.getUserId())
                .orElseThrow(() -> new AccessDeniedException("Not a parent"));
        if (studentId == null || !parentStudentRepository.existsByIdParentIdAndIdStudentId(parent.getId(), studentId)) {
            throw new AccessDeniedException("Student is not linked to this parent");
        }
    }

    private List<Long> sections(AppUserPrincipal actor, Audience audience, Long studentId, Semester semester) {
        List<Long> ids = switch (audience) {
            case TEACHER -> teacherRepository.findByUserId(actor.getUserId())
                    .map(t -> classSectionRepository.findIdsByTeacherAndSemester(t.getId(), semester.getId()))
                    .orElse(List.of());
            case STUDENT -> studentRepository.findByUserId(actor.getUserId())
                    .map(s -> enrollmentRepository.findEnrolledSectionIds(s.getId(), semester.getId()))
                    .orElse(List.of());
            case PARENT -> {
                requireChild(actor, studentId);
                yield enrollmentRepository.findEnrolledSectionIds(studentId, semester.getId());
            }
        };
        return ids.isEmpty() ? CatalogSupport.NO_IDS : ids;
    }

    private Semester resolveSemester(Long semesterId) {
        if (semesterId != null) {
            return semesterRepository.findById(semesterId)
                    .orElseThrow(() -> new NotFoundException(CatalogMessages.SEMESTER_NOT_FOUND));
        }
        return semesterRepository.findByCurrentTrue()
                .or(() -> semesterRepository.findAllByOrderByStartDateDesc().stream().findFirst())
                .orElseThrow(() -> new NotFoundException(CatalogMessages.SEMESTER_NOT_FOUND));
    }

    private static SemesterView toView(Semester semester) {
        return new SemesterView(semester.getId(), semester.getCode(), semester.getName(), semester.getStartDate(),
                semester.getEndDate(), semester.isCurrent());
    }

    static ScheduleItem toItem(ClassSession session) {
        ClassSection section = session.getClassSection();
        return new ScheduleItem(session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                section.getSubject().getCode(), section.getSubject().getName(), section.getCode(),
                session.getRoom() == null ? null : session.getRoom().getCode(),
                section.getTeacher().getUser().getFullName(), session.getStatus(), session.getNote());
    }
}
