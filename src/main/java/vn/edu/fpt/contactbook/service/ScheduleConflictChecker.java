package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;
import vn.edu.fpt.contactbook.dto.ScheduleConflict.Type;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Exam;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kiểm tra xung đột lịch theo khoảng thời gian chồng nhau (SRS FR-SCHEDULE-01, 02). Hai khoảng [a, b) và [c, d)
 * chồng nhau khi a < d và c < b, nên buổi kết thúc đúng lúc buổi kia bắt đầu không tính là trùng.
 * Buổi CANCELLED không tính, trừ khi trùng đúng ngày và giờ bắt đầu trong cùng lớp (uq_sess_cs_slot không lọc
 * trạng thái). Dữ liệu được nạp một lần cho cả khoảng ngày rồi so trong bộ nhớ, không truy vấn theo từng buổi.
 * Gọi trong transaction SERIALIZABLE của service ghi lịch để kết quả còn đúng tới lúc lưu.
 */
@Component
@RequiredArgsConstructor
public class ScheduleConflictChecker {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final ClassSessionRepository classSessionRepository;
    private final ExamRepository examRepository;
    private final EnrollmentRepository enrollmentRepository;

    /** Một khoảng giờ trong một ngày. */
    public record Slot(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * Xung đột của các buổi học định tạo hoặc định sửa cho lớp section: buổi khác của chính lớp, cùng phòng
     * (buổi học và lịch thi), cùng giảng viên, chung sinh viên đang ENROLLED (buổi học và lịch thi), lịch thi
     * của chính lớp. excludeSessionId là buổi đang sửa.
     */
    public List<ScheduleConflict> checkSessions(ClassSection section, Room room, List<Slot> slots,
                                                Long excludeSessionId) {
        return checkSessions(section, room, slots, excludeSessionId, false);
    }

    /**
     * Chỉ kiểm tra trùng phòng (buổi học và lịch thi khác dùng phòng đó). Dùng khi buổi giữ nguyên ngày giờ và
     * chỉ đổi phòng, để các xung đột giảng viên/sinh viên không liên quan tới việc đổi phòng không chặn thao tác.
     */
    public List<ScheduleConflict> checkRoom(ClassSection section, Room room, Slot slot, Long excludeSessionId) {
        return checkSessions(section, room, List.of(slot), excludeSessionId, true);
    }

    private List<ScheduleConflict> checkSessions(ClassSection section, Room room, List<Slot> slots,
                                                 Long excludeSessionId, boolean roomOnly) {
        if (slots.isEmpty() || (roomOnly && room == null)) {
            return List.of();
        }
        LocalDate from = slots.stream().map(Slot::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = slots.stream().map(Slot::date).max(Comparator.naturalOrder()).orElseThrow();
        Long roomId = room == null ? null : room.getId();
        Long teacherId = roomOnly ? null : section.getTeacher().getId();
        Map<Long, Long> shared = roomOnly ? Map.of() : sharedStudents(section.getId());

        List<ClassSession> own = roomOnly ? List.of() : classSessionRepository
                .findByClassSectionIdAndSessionDateBetween(section.getId(), from, to).stream()
                .filter(s -> !Objects.equals(s.getId(), excludeSessionId)).toList();
        List<ClassSession> others = classSessionRepository.findPossibleConflicts(section.getId(), from, to, roomId,
                teacherId, ids(shared.keySet()));
        List<Long> examSections = roomOnly ? CatalogSupport.NO_IDS
                : Stream.concat(Stream.of(section.getId()), shared.keySet().stream()).toList();
        List<Exam> exams = roomId == null && roomOnly ? List.of()
                : examRepository.findPossibleConflicts(from, to, null, roomId, examSections);

        List<ScheduleConflict> conflicts = new ArrayList<>();
        for (Slot slot : slots) {
            for (ClassSession other : own) {
                if (!other.getSessionDate().equals(slot.date())) {
                    continue;
                }
                if (other.getStatus() == SessionStatus.CANCELLED) {
                    if (other.getStartTime().equals(slot.startTime())) {
                        conflicts.add(conflict(slot, Type.CANCELLED_SLOT, "Lớp đã có buổi " + other.getSessionNo()
                                + " (đã hủy) bắt đầu lúc " + time(other.getStartTime())
                                + "; hãy chọn giờ bắt đầu khác"));
                    }
                } else if (overlaps(slot, other.getStartTime(), other.getEndTime())) {
                    conflicts.add(conflict(slot, Type.SAME_SECTION,
                            "Lớp đã có buổi " + other.getSessionNo() + " " + range(other)));
                }
            }
            for (ClassSession other : others) {
                if (!other.getSessionDate().equals(slot.date())
                        || !overlaps(slot, other.getStartTime(), other.getEndTime())) {
                    continue;
                }
                String label = other.getClassSection().getCode() + " " + range(other);
                if (roomId != null && other.getRoom() != null && roomId.equals(other.getRoom().getId())) {
                    conflicts.add(conflict(slot, Type.ROOM, "Phòng " + room.getCode() + " đã có lớp " + label));
                }
                if (teacherId != null && teacherId.equals(other.getClassSection().getTeacher().getId())) {
                    conflicts.add(conflict(slot, Type.TEACHER, "Giáo viên đang dạy lớp " + label));
                }
                Long count = shared.get(other.getClassSection().getId());
                if (count != null) {
                    conflicts.add(conflict(slot, Type.STUDENT, count + " học sinh của lớp này cũng học lớp " + label));
                }
            }
            for (Exam exam : exams) {
                if (!exam.getExamDate().equals(slot.date())
                        || !overlaps(slot, exam.getStartTime(), exam.getEndTime())) {
                    continue;
                }
                Long examSectionId = exam.getClassSection().getId();
                String label = "lịch thi lớp " + exam.getClassSection().getCode() + " " + range(exam);
                if (roomId != null && exam.getRoom() != null && roomId.equals(exam.getRoom().getId())) {
                    conflicts.add(conflict(slot, Type.ROOM, "Phòng " + room.getCode() + " có " + label));
                }
                if (examSectionId.equals(section.getId()) && !roomOnly) {
                    conflicts.add(conflict(slot, Type.SAME_SECTION, "Lớp có " + label));
                } else if (shared.containsKey(examSectionId)) {
                    conflicts.add(conflict(slot, Type.STUDENT,
                            shared.get(examSectionId) + " học sinh của lớp này có " + label));
                }
            }
        }
        return conflicts;
    }

    /**
     * Xung đột của một lịch thi định tạo hoặc định sửa: cùng phòng (buổi học và lịch thi khác), lịch thi và buổi
     * học khác của chính lớp, lịch thi và buổi học của lớp có chung sinh viên đang ENROLLED.
     */
    public List<ScheduleConflict> checkExam(ClassSection section, Room room, Slot slot, Long excludeExamId) {
        Long roomId = room == null ? null : room.getId();
        Map<Long, Long> shared = sharedStudents(section.getId());
        List<Long> withOwn = Stream.concat(Stream.of(section.getId()), shared.keySet().stream()).toList();

        List<ScheduleConflict> conflicts = new ArrayList<>();
        for (Exam other : examRepository.findPossibleConflicts(slot.date(), slot.date(), excludeExamId, roomId,
                withOwn)) {
            if (!overlaps(slot, other.getStartTime(), other.getEndTime())) {
                continue;
            }
            Long otherSectionId = other.getClassSection().getId();
            String label = "lịch thi lớp " + other.getClassSection().getCode() + " " + range(other);
            if (roomId != null && other.getRoom() != null && roomId.equals(other.getRoom().getId())) {
                conflicts.add(conflict(slot, Type.ROOM, "Phòng " + room.getCode() + " đã có " + label));
            }
            if (otherSectionId.equals(section.getId())) {
                conflicts.add(conflict(slot, Type.SAME_SECTION, "Lớp đã có " + label));
            } else if (shared.containsKey(otherSectionId)) {
                conflicts.add(conflict(slot, Type.STUDENT,
                        shared.get(otherSectionId) + " học sinh của lớp này cũng thi " + label));
            }
        }
        for (ClassSession own : classSessionRepository.findByClassSectionIdAndSessionDateBetween(section.getId(),
                slot.date(), slot.date())) {
            if (own.getStatus() != SessionStatus.CANCELLED && overlaps(slot, own.getStartTime(), own.getEndTime())) {
                conflicts.add(conflict(slot, Type.SAME_SECTION, "Lớp có buổi học " + own.getSessionNo() + " "
                        + range(own)));
            }
        }
        for (ClassSession other : classSessionRepository.findPossibleConflicts(section.getId(), slot.date(),
                slot.date(), roomId, null, ids(shared.keySet()))) {
            if (!overlaps(slot, other.getStartTime(), other.getEndTime())) {
                continue;
            }
            String label = "lớp " + other.getClassSection().getCode() + " " + range(other);
            if (roomId != null && other.getRoom() != null && roomId.equals(other.getRoom().getId())) {
                conflicts.add(conflict(slot, Type.ROOM, "Phòng " + room.getCode() + " đang có buổi học " + label));
            }
            Long count = shared.get(other.getClassSection().getId());
            if (count != null) {
                conflicts.add(conflict(slot, Type.STUDENT, count + " học sinh của lớp này đang học " + label));
            }
        }
        return conflicts;
    }

    /** Lớp khác có chung sinh viên đang ENROLLED → số sinh viên chung. */
    private Map<Long, Long> sharedStudents(Long sectionId) {
        return enrollmentRepository.countSharedStudents(sectionId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private static Collection<Long> ids(Set<Long> ids) {
        return ids.isEmpty() ? CatalogSupport.NO_IDS : ids;
    }

    private static boolean overlaps(Slot slot, LocalTime start, LocalTime end) {
        return overlaps(slot.startTime(), slot.endTime(), start, end);
    }

    private static ScheduleConflict conflict(Slot slot, Type type, String detail) {
        return new ScheduleConflict(slot.date(), slot.startTime(), slot.endTime(), type, detail);
    }

    private static String range(ClassSession session) {
        return "(" + time(session.getStartTime()) + "–" + time(session.getEndTime()) + ")";
    }

    private static String range(Exam exam) {
        return "(" + time(exam.getStartTime()) + "–" + time(exam.getEndTime()) + ")";
    }

    private static String time(LocalTime time) {
        return time.format(TIME);
    }
}
