package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttendanceHistoryItem;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.SectionAttendanceRow;
import vn.edu.fpt.contactbook.dto.SessionView;
import vn.edu.fpt.contactbook.dto.StudentAttendanceRow;
import vn.edu.fpt.contactbook.dto.TeacherSectionView;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Xem điểm danh (SRS FR-ATT-02, 04). Phạm vi kiểm tra ở đây: giảng viên chỉ lớp mình dạy; trưởng bộ môn lớp thuộc
 * bộ môn mình; ADMIN mọi lớp (AttendanceScope); sinh viên chỉ của mình; phụ huynh chỉ con đã liên kết
 * (ScheduleViewService.requireChild, con khác → 403). Tỷ lệ và cảnh báo tính bằng AttendanceRule.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceViewService {

    private final AttendanceScope scope;
    private final AttendanceRule rule;
    private final ScheduleViewService scheduleViewService;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ClassSectionRepository classSectionRepository;
    private final ClassSessionRepository classSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;

    /** Thống kê một lớp: buổi học (để mở bảng điểm danh) và từng sinh viên đang học, mỗi danh sách một trang. */
    public record SectionAttendance(TeacherSectionView section, Page<SessionView> sessions,
                                    Page<SectionAttendanceRow> students, long totalSessions) {
    }

    /** Lịch sử theo môn của một sinh viên: dòng tổng hợp và từng buổi. */
    public record StudentHistory(StudentAttendanceRow summary, Page<AttendanceHistoryItem> sessions) {
    }

    public int thresholdPercent() {
        return rule.thresholdPercent();
    }

    /** Lớp học phần của học kỳ mà giảng viên đang đăng nhập dạy; chưa có hồ sơ giảng viên thì danh sách rỗng. */
    public InSemester<Page<TeacherSectionView>> teacherSections(AppUserPrincipal actor, Long semesterId, int page) {
        Semester semester = scheduleViewService.resolveSemester(semesterId);
        Page<TeacherSectionView> sections = teacherRepository.findByUserId(actor.getUserId())
                .map(teacher -> classSectionRepository.findByTeacherIdAndSemesterIdOrderByCodeAsc(teacher.getId(),
                        semester.getId(), CatalogSupport.page(page)))
                .orElse(Page.empty(CatalogSupport.page(page)))
                .map(AttendanceViewService::toSectionView);
        return new InSemester<>(ScheduleViewService.toView(semester), sections);
    }

    public SectionAttendance section(AppUserPrincipal actor, Access access, Long sectionId, int sessionPage,
                                     int studentPage) {
        ClassSection section = scope.section(actor, access, sectionId, false);
        Set<Long> withAttendance = attendanceRepository.findSessionIdsWithAttendance(sectionId);
        Page<SessionView> sessions = classSessionRepository.findByClassSectionIdOrderBySessionDateAscStartTimeAsc(
                        sectionId, CatalogSupport.page(sessionPage))
                .map(s -> ClassSessionService.toView(s, withAttendance));

        long total = classSessionRepository.countByClassSectionIdAndStatusNot(sectionId, SessionStatus.CANCELLED);
        Page<Enrollment> enrollments = enrollmentRepository.findPageBySection(sectionId, EnrollmentStatus.ENROLLED,
                CatalogSupport.page(studentPage));
        List<Long> studentIds = enrollments.map(e -> e.getStudent().getId()).getContent();
        Map<Long, Map<AttendanceStatus, Long>> counts = studentIds.isEmpty() ? Map.of()
                : group(attendanceRepository.countByStudentAndStatus(sectionId, studentIds));
        Page<SectionAttendanceRow> students = enrollments.map(e -> {
            Student student = e.getStudent();
            return new SectionAttendanceRow(student.getId(), student.getStudentCode(), student.getUser().getFullName(),
                    rule.summarize(counts.getOrDefault(student.getId(), Map.of()), total));
        });
        return new SectionAttendance(toSectionView(section), sessions, students, total);
    }

    /** Điểm danh theo môn trong học kỳ: các lớp sinh viên đang học hoặc đã học xong (không tính lớp đã rút). */
    public InSemester<List<StudentAttendanceRow>> student(AppUserPrincipal actor, Audience audience, Long studentId,
                                                          Long semesterId) {
        Long id = studentId(actor, audience, studentId);
        Semester semester = scheduleViewService.resolveSemester(semesterId);
        List<ClassSection> sections = enrollmentRepository.findAttendedInSemester(id, semester.getId()).stream()
                .map(Enrollment::getClassSection).toList();
        if (sections.isEmpty()) {
            return new InSemester<>(ScheduleViewService.toView(semester), List.of());
        }
        List<Long> sectionIds = sections.stream().map(ClassSection::getId).toList();
        Map<Long, Map<AttendanceStatus, Long>> counts = group(attendanceRepository.countBySectionAndStatus(id,
                sectionIds));
        Map<Long, Long> totals = classSessionRepository.countActiveBySections(sectionIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        List<StudentAttendanceRow> rows = sections.stream()
                .map(section -> toStudentRow(section, rule.summarize(counts.getOrDefault(section.getId(), Map.of()),
                        totals.getOrDefault(section.getId(), 0L))))
                .toList();
        return new InSemester<>(ScheduleViewService.toView(semester), rows);
    }

    /** Lịch sử từng buổi của một môn; sinh viên phải đang học hoặc đã học xong lớp này, nếu không → 403. */
    public StudentHistory history(AppUserPrincipal actor, Audience audience, Long studentId, Long sectionId,
                                  int page) {
        Long id = studentId(actor, audience, studentId);
        ClassSection section = enrollmentRepository.findByClassSectionIdAndStudentId(sectionId, id)
                .filter(e -> e.getStatus() != EnrollmentStatus.DROPPED)
                .map(Enrollment::getClassSection)
                .orElseThrow(() -> new AccessDeniedException("Student is not in this class section"));

        Page<ClassSession> sessions = classSessionRepository.findByClassSectionIdOrderBySessionDateAscStartTimeAsc(
                sectionId, CatalogSupport.page(page));
        List<Long> sessionIds = sessions.map(ClassSession::getId).getContent();
        Map<Long, Attendance> marks = sessionIds.isEmpty() ? Map.of()
                : attendanceRepository.findByStudentIdAndSessionIdIn(id, sessionIds).stream()
                .collect(Collectors.toMap(a -> a.getSession().getId(), Function.identity()));
        Page<AttendanceHistoryItem> items = sessions.map(s -> {
            Attendance mark = marks.get(s.getId());
            return new AttendanceHistoryItem(s.getSessionNo(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                    s.getRoom() == null ? null : s.getRoom().getCode(), s.getStatus(),
                    mark == null ? null : mark.getStatus(), mark == null ? null : mark.getNote());
        });

        Map<AttendanceStatus, Long> counts = group(attendanceRepository.countBySectionAndStatus(id,
                List.of(sectionId))).getOrDefault(sectionId, Map.of());
        long total = classSessionRepository.countByClassSectionIdAndStatusNot(sectionId, SessionStatus.CANCELLED);
        return new StudentHistory(toStudentRow(section, rule.summarize(counts, total)), items);
    }

    /** STUDENT: hồ sơ sinh viên của chính mình (không có → 403); PARENT: con đã liên kết. */
    private Long studentId(AppUserPrincipal actor, Audience audience, Long studentId) {
        return switch (audience) {
            case STUDENT -> studentRepository.findByUserId(actor.getUserId())
                    .map(Student::getId)
                    .orElseThrow(() -> new AccessDeniedException("No student profile"));
            case PARENT -> {
                scheduleViewService.requireChild(actor, studentId);
                yield studentId;
            }
            case TEACHER -> throw new AccessDeniedException("Teachers view attendance per class section");
        };
    }

    /** [khóa, AttendanceStatus, count] → khóa → (trạng thái → count). */
    private static Map<Long, Map<AttendanceStatus, Long>> group(List<Object[]> rows) {
        Map<Long, Map<AttendanceStatus, Long>> result = new HashMap<>();
        for (Object[] row : rows) {
            result.computeIfAbsent((Long) row[0], key -> new EnumMap<>(AttendanceStatus.class))
                    .put((AttendanceStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    private static TeacherSectionView toSectionView(ClassSection section) {
        return new TeacherSectionView(section.getId(), section.getCode(), section.getSubject().getCode(),
                section.getSubject().getName(), section.getSemester().getCode(), section.getStatus());
    }

    private static StudentAttendanceRow toStudentRow(ClassSection section, AttendanceSummary summary) {
        return new StudentAttendanceRow(section.getId(), section.getCode(), section.getSubject().getCode(),
                section.getSubject().getName(), summary);
    }
}
