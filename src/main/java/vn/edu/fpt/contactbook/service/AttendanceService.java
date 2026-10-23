package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.dto.AttendanceSaveResult;
import vn.edu.fpt.contactbook.dto.AttendanceSheet;
import vn.edu.fpt.contactbook.dto.AttendanceSummary;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Điểm danh theo buổi (SRS FR-ATT-01, 03, UC-04): ba trạng thái (có mặt, vắng có phép, vắng không phép) và ghi chú
 * của từng học sinh. Chỉ buổi chưa hủy, ngày không sau hôm nay (Asia/Ho_Chi_Minh), của lớp chưa hủy; sessionId phải
 * thuộc đúng lớp.
 * <ul>
 *   <li>Hạn của giáo viên (AttendanceEditWindow): điểm danh lần đầu tới giờ kết thúc buổi cộng 24 giờ, sửa tới lần
 *       điểm danh đầu cộng 24 giờ. Quá hạn thì giáo viên chỉ xem.</li>
 *   <li>Lần lưu đầu do giáo viên của lớp làm, không cần lý do. ADMIN chỉ điểm danh thay khi giáo viên đã hết hạn, kèm
 *       lý do.</li>
 *   <li>Các lần sau là điều chỉnh (giáo viên còn hạn, hoặc ADMIN bất kỳ lúc nào): đổi trạng thái bắt buộc lý do;
 *       chỉ sửa ghi chú thì không. Lý do chỉ ghi vào nhật ký (không đè ghi chú), marked_by thành người sửa, mỗi
 *       học sinh bị đổi một dòng audit cũ → mới.</li>
 * </ul>
 * Mỗi lần lưu khóa dòng lớp học phần tới hết transaction, nên hai lần lưu cùng buổi không chạy chồng nhau.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int REASON_MAX = 255;
    private static final int NOTE_MAX = 255;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final AttendanceScope scope;
    private final ClassSectionRepository classSectionRepository;
    private final ClassSessionRepository classSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AttendanceRule rule;
    private final NotificationService notificationService;
    private final NotificationAudience audience;
    private final UserRequestRepository userRequestRepository;
    private final AttendanceEditWindow editWindow;

    @Transactional(readOnly = true)
    public AttendanceSheet sheet(AppUserPrincipal actor, Access access, Long sectionId, Long sessionId) {
        ClassSection section = scope.section(actor, access, sectionId, false);
        ClassSession session = findSession(sectionId, sessionId);
        requireMarkable(section, session);
        Map<Long, Attendance> existing = existing(sessionId);
        List<Student> students = enrolled(sectionId).stream().map(Enrollment::getStudent).toList();
        Set<Long> onLeave = students.isEmpty() ? Set.of() : Set.copyOf(userRequestRepository
                .findStudentsWithApprovedLeave(students.stream().map(Student::getId).toList(), sectionId,
                        session.getSessionDate()));
        List<AttendanceSheet.Row> rows = students.stream()
                .map(student -> {
                    Attendance row = existing.get(student.getId());
                    return new AttendanceSheet.Row(student.getId(), student.getStudentCode(),
                            student.getUser().getFullName(), row == null ? null : row.getStatus(),
                            row == null ? null : row.getNote(), onLeave.contains(student.getId()));
                })
                .toList();
        LocalDateTime deadline = deadline(session, existing);
        boolean teacherOpen = editWindow.isOpen(deadline, AppTime.now());
        return new AttendanceSheet(section.getId(), section.getCode(), section.getSubject().getCode(),
                section.getSubject().getName(), session.getId(), session.getSessionNo(), session.getSessionDate(),
                session.getStartTime(), session.getEndTime(),
                session.getRoom() == null ? null : session.getRoom().getCode(), !existing.isEmpty(), deadline,
                access == Access.TEACHER && !teacherOpen, !existing.isEmpty() || access == Access.MANAGER, rows);
    }

    @Transactional
    public AttendanceSaveResult save(AppUserPrincipal actor, Access access, Long sectionId, Long sessionId,
                                     AttendanceForm form, String ipAddress) {
        try {
            scope.section(actor, access, sectionId, true);
            ClassSection section = classSectionRepository.findByIdForUpdate(sectionId)
                    .orElseThrow(() -> new NotFoundException(SectionMessages.SECTION_NOT_FOUND));
            ClassSession session = findSession(sectionId, sessionId);
            requireMarkable(section, session);
            Map<Long, Attendance> existing = existing(sessionId);
            LocalDateTime deadline = deadline(session, existing);
            boolean teacherOpen = editWindow.isOpen(deadline, AppTime.now());
            if (access == Access.TEACHER && !teacherOpen) {
                throw new BusinessException(AttendanceMessages.EDIT_LOCKED.formatted(deadline.format(DATE_TIME)));
            }
            if (existing.isEmpty() && access == Access.MANAGER && teacherOpen) {
                throw new BusinessException(AttendanceMessages.NOT_MARKED_YET.formatted(deadline.format(DATE_TIME)));
            }
            List<Student> students = enrolled(sectionId).stream().map(Enrollment::getStudent).toList();
            Map<Long, AttendanceStatus> statuses = requireStatuses(students, form.getStatuses());
            Map<Long, String> notes = notes(form.getNotes());
            User marker = userRepository.getReferenceById(actor.getUserId());
            Map<Long, AttendanceSummary> before = summaries(section, students);
            AttendanceSaveResult result;
            if (existing.isEmpty()) {
                // ADMIN điểm danh thay sau hạn của giáo viên: bắt buộc lý do, ghi vào nhật ký.
                String reason = access == Access.MANAGER ? requireReason(form.getReason()) : null;
                result = markFirstTime(actor, section, session, students, statuses, notes, reason, marker,
                        ipAddress);
            } else {
                result = adjust(actor, section, session, students, statuses, notes, existing, form.getReason(),
                        marker, ipAddress);
            }
            attendanceRepository.flush();
            notifyNewWarnings(actor, section, students, before);
            return result;
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(AttendanceMessages.CONCURRENT_CHANGE);
        }
    }

    private AttendanceSaveResult markFirstTime(AppUserPrincipal actor, ClassSection section, ClassSession session,
                                               List<Student> students, Map<Long, AttendanceStatus> statuses,
                                               Map<Long, String> notes, String reason, User marker,
                                               String ipAddress) {
        List<Attendance> rows = students.stream()
                .map(student -> newRow(session, student, statuses.get(student.getId()), notes.get(student.getId()),
                        marker))
                .toList();
        attendanceRepository.saveAll(rows);
        String counts = Arrays.stream(AttendanceStatus.values())
                .map(status -> status + "=" + statuses.values().stream().filter(status::equals).count())
                .collect(Collectors.joining(", "));
        auditService.log(actor.getUserId(), AuditActions.MARK_ATTENDANCE, AuditActions.ENTITY_CLASS_SESSION,
                session.getId(), sessionDetail(section, session) + ", " + counts
                        + (reason == null ? "" : ", reason=" + reason), ipAddress);
        return new AttendanceSaveResult(true, rows.size());
    }

    /**
     * Chỉ dòng đổi trạng thái hoặc ghi chú (hoặc học sinh chưa có dòng) mới được ghi và audit. Có dòng đổi trạng
     * thái thì bắt buộc lý do; lý do chỉ ghi vào nhật ký.
     */
    private AttendanceSaveResult adjust(AppUserPrincipal actor, ClassSection section, ClassSession session,
                                        List<Student> students, Map<Long, AttendanceStatus> statuses,
                                        Map<Long, String> notes, Map<Long, Attendance> existing, String rawReason,
                                        User marker, String ipAddress) {
        List<Student> changed = students.stream()
                .filter(student -> {
                    Attendance row = existing.get(student.getId());
                    return row == null || row.getStatus() != statuses.get(student.getId())
                            || (notes.containsKey(student.getId())
                            && !Objects.equals(CatalogSupport.trimToNull(row.getNote()), notes.get(student.getId())));
                })
                .toList();
        if (changed.isEmpty()) {
            return new AttendanceSaveResult(false, 0);
        }
        boolean statusChanged = changed.stream().anyMatch(student -> {
            Attendance row = existing.get(student.getId());
            return row == null || row.getStatus() != statuses.get(student.getId());
        });
        String reason = statusChanged ? requireReason(rawReason) : CatalogSupport.trimToNull(rawReason);
        if (reason != null && reason.length() > REASON_MAX) {
            throw new BusinessException(AttendanceMessages.REASON_SIZE, "reason");
        }

        List<Change> changes = new ArrayList<>();
        for (Student student : changed) {
            AttendanceStatus newStatus = statuses.get(student.getId());
            Attendance row = existing.get(student.getId());
            if (row == null) {
                row = newRow(session, student, newStatus, notes.get(student.getId()), marker);
                changes.add(new Change(row, student, null, null));
            } else {
                changes.add(new Change(row, student, row.getStatus(), row.getNote()));
                row.setStatus(newStatus);
                if (notes.containsKey(student.getId())) {
                    row.setNote(notes.get(student.getId()));
                }
                row.setMarkedBy(marker);
            }
        }
        attendanceRepository.saveAllAndFlush(changes.stream().map(Change::row).toList());
        String sessionDetail = sessionDetail(section, session);
        for (Change change : changes) {
            auditService.log(actor.getUserId(), AuditActions.ADJUST_ATTENDANCE, AuditActions.ENTITY_ATTENDANCE,
                    change.row().getId(), sessionDetail + ", student=" + change.student().getStudentCode()
                            + ", status=" + (change.oldStatus() == null ? "-" : change.oldStatus()) + " -> "
                            + change.row().getStatus() + ", note=" + orDash(change.oldNote()) + " -> "
                            + orDash(change.row().getNote()) + ", reason=" + orDash(reason),
                    ipAddress);
        }
        return new AttendanceSaveResult(false, changes.size());
    }

    private static String requireReason(String rawReason) {
        String reason = CatalogSupport.trimToNull(rawReason);
        if (reason == null) {
            throw new BusinessException(AttendanceMessages.REASON_REQUIRED, "reason");
        }
        if (reason.length() > REASON_MAX) {
            throw new BusinessException(AttendanceMessages.REASON_SIZE, "reason");
        }
        return reason;
    }

    /**
     * Ghi chú của từng học sinh gửi lên: bỏ khoảng trắng, rỗng thành null (xóa ghi chú), quá 255 ký tự báo lỗi. Học
     * sinh không có trong map (client API không gửi notes, hoặc chỉ gửi một phần) thì giữ nguyên ghi chú đã lưu.
     */
    private static Map<Long, String> notes(Map<Long, String> submitted) {
        Map<Long, String> notes = new HashMap<>();
        if (submitted != null) {
            submitted.forEach((studentId, raw) -> {
                String note = CatalogSupport.trimToNull(raw);
                if (note != null && note.length() > NOTE_MAX) {
                    throw new BusinessException(AttendanceMessages.NOTE_SIZE);
                }
                notes.put(studentId, note);
            });
        }
        return notes;
    }

    /** Hạn của giáo viên: tính từ lần điểm danh đầu của buổi, chưa điểm danh thì từ giờ kết thúc buổi. */
    private LocalDateTime deadline(ClassSession session, Map<Long, Attendance> existing) {
        LocalDateTime firstMarkedAt = existing.values().stream().map(Attendance::getMarkedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return editWindow.deadline(firstMarkedAt, session.getSessionDate(), session.getEndTime());
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private record Change(Attendance row, Student student, AttendanceStatus oldStatus, String oldNote) {
    }

    /** Tổng hợp chuyên cần hiện tại của từng sinh viên (students.id) trong lớp (AttendanceRule). */
    private Map<Long, AttendanceSummary> summaries(ClassSection section, List<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        long total = classSessionRepository.countByClassSectionIdAndStatusNot(section.getId(),
                SessionStatus.CANCELLED);
        Map<Long, Map<AttendanceStatus, Long>> counts = AttendanceViewService.group(
                attendanceRepository.countByStudentAndStatus(section.getId(),
                        students.stream().map(Student::getId).toList()));
        return students.stream().collect(Collectors.toMap(Student::getId,
                s -> rule.summarize(counts.getOrDefault(s.getId(), Map.of()), total)));
    }

    /**
     * Báo sinh viên và phụ huynh khi tỷ lệ vắng VỪA vượt ngưỡng sau lần lưu này (trước không bị cảnh báo, sau bị
     * cảnh báo); đã vượt từ trước thì không gửi lặp. Gửi trong cùng transaction, người gửi là người điểm danh.
     */
    private void notifyNewWarnings(AppUserPrincipal actor, ClassSection section, List<Student> students,
                                   Map<Long, AttendanceSummary> before) {
        Map<Long, AttendanceSummary> after = summaries(section, students);
        for (Student student : students) {
            AttendanceSummary was = before.get(student.getId());
            AttendanceSummary now = after.get(student.getId());
            if ((was != null && was.warning()) || now == null || !now.warning()) {
                continue;
            }
            notificationService.sendAutomatic(actor.getUserId(),
                    "[" + section.getCode() + "] Cảnh báo vắng vượt " + rule.thresholdPercent() + "%",
                    "Học sinh " + student.getUser().getFullName() + " (" + student.getStudentCode() + ") đã vắng "
                            + now.absent() + "/" + now.totalSessions() + " buổi ("
                            + now.absentRate().toPlainString().replace('.', ',') + "%) môn "
                            + section.getSubject().getCode() + " - " + section.getSubject().getName() + " (lớp "
                            + section.getCode() + "), vượt ngưỡng " + rule.thresholdPercent() + "%.",
                    NotificationPriority.URGENT, NotificationTargetType.USER, student.getUser().getId(),
                    audience.studentsAndParents(List.of(student.getId())));
        }
    }

    /**
     * Form phải có trạng thái cho đúng các sinh viên đang học: thiếu một người → yêu cầu chọn đủ; có người không còn
     * trong lớp → danh sách đã đổi (trang cũ).
     */
    private static Map<Long, AttendanceStatus> requireStatuses(List<Student> students,
                                                               Map<Long, AttendanceStatus> submitted) {
        if (students.isEmpty()) {
            throw new BusinessException(AttendanceMessages.NO_STUDENTS);
        }
        Map<Long, AttendanceStatus> statuses = submitted == null ? Map.of() : submitted;
        if (!students.stream().map(Student::getId).collect(Collectors.toSet()).containsAll(statuses.keySet())) {
            throw new BusinessException(AttendanceMessages.STUDENTS_CHANGED);
        }
        if (students.stream().anyMatch(student -> statuses.get(student.getId()) == null)) {
            throw new BusinessException(AttendanceMessages.STATUS_MISSING);
        }
        return statuses;
    }

    private static void requireMarkable(ClassSection section, ClassSession session) {
        if (section.getStatus() == SectionStatus.CANCELLED) {
            throw new BusinessException(AttendanceMessages.SECTION_CANCELLED);
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BusinessException(AttendanceMessages.SESSION_CANCELLED);
        }
        if (session.getSessionDate().isAfter(AppTime.today())) {
            throw new BusinessException(AttendanceMessages.FUTURE_SESSION.formatted(
                    session.getSessionDate().format(DATE)));
        }
    }

    private ClassSession findSession(Long sectionId, Long sessionId) {
        return classSessionRepository.findById(sessionId)
                .filter(s -> Objects.equals(s.getClassSection().getId(), sectionId))
                .orElseThrow(() -> new NotFoundException(ScheduleMessages.SESSION_NOT_FOUND));
    }

    private Map<Long, Attendance> existing(Long sessionId) {
        return attendanceRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
    }

    private List<Enrollment> enrolled(Long sectionId) {
        return enrollmentRepository.findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(sectionId,
                EnrollmentStatus.ENROLLED);
    }

    private static Attendance newRow(ClassSession session, Student student, AttendanceStatus status, String note,
                                     User marker) {
        Attendance row = new Attendance();
        row.setSession(session);
        row.setStudent(student);
        row.setStatus(status);
        row.setNote(note);
        row.setMarkedBy(marker);
        return row;
    }

    private static String sessionDetail(ClassSection section, ClassSession session) {
        return "section=" + section.getCode() + ", session=" + session.getSessionNo() + ", date="
                + session.getSessionDate();
    }
}
