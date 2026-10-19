package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;
import vn.edu.fpt.contactbook.dto.SessionForm;
import vn.edu.fpt.contactbook.dto.SessionView;
import vn.edu.fpt.contactbook.dto.WeeklyScheduleForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.RoomRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleConflictChecker.Slot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Buổi học của lớp học phần (SRS FR-SCHEDULE-01): thêm buổi lẻ (MAKEUP), tạo lịch hàng tuần (SCHEDULED), sửa,
 * hủy (CANCELLED, không xóa). ADMIN và trưởng bộ môn của môn (ClassSectionService.findInScope). Mọi thao tác ghi
 * chạy SERIALIZABLE và khóa dòng lớp học phần, để kiểm tra xung đột và session_no còn đúng tới lúc lưu.
 */
@Service
@RequiredArgsConstructor
public class ClassSessionService {

    static final int MAX_WEEKLY_SESSIONS = 200;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClassSessionRepository classSessionRepository;
    private final ClassSectionRepository classSectionRepository;
    private final RoomRepository roomRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClassSectionService classSectionService;
    private final ScheduleConflictChecker conflictChecker;
    private final ScheduleChangeNotifier notifier;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<SessionView> sessions(AppUserPrincipal actor, Long sectionId, int page) {
        classSectionService.findInScope(actor, sectionId);
        Set<Long> withAttendance = attendanceRepository.findSessionIdsWithAttendance(sectionId);
        return classSessionRepository.findByClassSectionIdOrderBySessionDateAscStartTimeAsc(sectionId,
                        CatalogSupport.page(page))
                .map(s -> toView(s, withAttendance));
    }

    /** withAttendance: id các buổi đã có điểm danh của lớp (AttendanceRepository.findSessionIdsWithAttendance). */
    static SessionView toView(ClassSession s, Set<Long> withAttendance) {
        return new SessionView(s.getId(), s.getSessionNo(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getRoom() == null ? null : s.getRoom().getCode(), s.getStatus(), s.getNote(),
                withAttendance.contains(s.getId()));
    }

    @Transactional(readOnly = true)
    public SessionForm editForm(AppUserPrincipal actor, Long sectionId, Long sessionId) {
        classSectionService.findInScope(actor, sectionId);
        ClassSession session = find(sectionId, sessionId);
        SessionForm form = new SessionForm();
        form.setSessionDate(session.getSessionDate());
        form.setStartTime(session.getStartTime());
        form.setEndTime(session.getEndTime());
        form.setRoomId(session.getRoom() == null ? null : session.getRoom().getId());
        form.setNote(session.getNote());
        return form;
    }

    /** Buổi lẻ ngoài lịch tuần: trạng thái MAKEUP, session_no nối tiếp. */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long create(AppUserPrincipal actor, Long sectionId, SessionForm form, String ipAddress) {
        return guarded(() -> {
            ClassSection section = lockForNewSchedule(actor, sectionId);
            Map<String, String> errors = new LinkedHashMap<>();
            checkTimes(form.getStartTime(), form.getEndTime(), errors);
            checkInSemester(form.getSessionDate(), section.getSemester(), "sessionDate", errors);
            Room room = room(form.getRoomId(), errors);
            String note = CatalogSupport.optionalText(form.getNote(), 255, "note", "Ghi chú tối đa 255 ký tự",
                    errors);
            CatalogSupport.throwIfAny(errors);
            requireNoConflicts(conflictChecker.checkSessions(section, room,
                    List.of(new Slot(form.getSessionDate(), form.getStartTime(), form.getEndTime())), null));

            ClassSession session = newSession(section, room, nextSessionNo(sectionId), form.getSessionDate(),
                    form.getStartTime(), form.getEndTime(), SessionStatus.MAKEUP);
            session.setNote(note);
            classSessionRepository.saveAndFlush(session);
            notifier.makeupSessionAdded(actor.getUserId(), session);
            auditService.log(actor.getUserId(), AuditActions.CREATE_SESSION, AuditActions.ENTITY_CLASS_SESSION,
                    session.getId(), detail(section, session), ipAddress);
            return session.getId();
        });
    }

    /**
     * Sửa ngày, giờ, phòng, ghi chú. Buổi đã hủy không sửa được; buổi đã có điểm danh chỉ đổi được phòng và
     * ghi chú. Đổi ngày giờ hoặc phòng thì kiểm tra lại xung đột (bỏ qua chính buổi này).
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void update(AppUserPrincipal actor, Long sectionId, Long sessionId, SessionForm form, String ipAddress) {
        guarded(() -> {
            ClassSection section = lockSection(actor, sectionId);
            ClassSession session = find(sectionId, sessionId);
            if (session.getStatus() == SessionStatus.CANCELLED) {
                throw new BusinessException(ScheduleMessages.SESSION_CANCELLED);
            }
            Map<String, String> errors = new LinkedHashMap<>();
            checkTimes(form.getStartTime(), form.getEndTime(), errors);
            checkInSemester(form.getSessionDate(), section.getSemester(), "sessionDate", errors);
            Room room = room(form.getRoomId(), errors);
            String note = CatalogSupport.optionalText(form.getNote(), 255, "note", "Ghi chú tối đa 255 ký tự",
                    errors);
            CatalogSupport.throwIfAny(errors);

            boolean timeChanged = !session.getSessionDate().equals(form.getSessionDate())
                    || !session.getStartTime().equals(form.getStartTime())
                    || !session.getEndTime().equals(form.getEndTime());
            Room previousRoom = session.getRoom();
            boolean roomChanged = !Objects.equals(roomId(previousRoom), roomId(room));
            if (timeChanged && attendanceRepository.existsBySessionId(sessionId)) {
                throw new BusinessException(ScheduleMessages.HAS_ATTENDANCE_TIME);
            }
            Slot slot = new Slot(form.getSessionDate(), form.getStartTime(), form.getEndTime());
            if (timeChanged) {
                requireNoConflicts(conflictChecker.checkSessions(section, room, List.of(slot), sessionId));
            } else if (roomChanged) {
                requireNoConflicts(conflictChecker.checkRoom(section, room, slot, sessionId));
            }

            LocalDate previousDate = session.getSessionDate();
            LocalTime previousStart = session.getStartTime();
            LocalTime previousEnd = session.getEndTime();
            session.setSessionDate(form.getSessionDate());
            session.setStartTime(form.getStartTime());
            session.setEndTime(form.getEndTime());
            session.setRoom(room);
            session.setNote(note);
            classSessionRepository.flush();
            // Đổi giờ: một thông báo (đã ghi phòng mới); chỉ đổi phòng thì báo đổi phòng.
            if (timeChanged) {
                notifier.sessionRescheduled(actor.getUserId(), session, previousDate, previousStart, previousEnd);
            } else if (roomChanged) {
                notifier.sessionRoomChanged(actor.getUserId(), session, previousRoom);
            }
            auditService.log(actor.getUserId(), AuditActions.UPDATE_SESSION, AuditActions.ENTITY_CLASS_SESSION,
                    sessionId, detail(section, session), ipAddress);
            return null;
        });
    }

    /** Hủy buổi: đổi sang CANCELLED, không xóa; buổi đã có điểm danh không hủy được. */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancel(AppUserPrincipal actor, Long sectionId, Long sessionId, String ipAddress) {
        guarded(() -> {
            ClassSection section = lockSection(actor, sectionId);
            ClassSession session = find(sectionId, sessionId);
            if (session.getStatus() == SessionStatus.CANCELLED) {
                throw new BusinessException(ScheduleMessages.SESSION_ALREADY_CANCELLED);
            }
            if (attendanceRepository.existsBySessionId(sessionId)) {
                throw new BusinessException(ScheduleMessages.HAS_ATTENDANCE_CANCEL);
            }
            session.setStatus(SessionStatus.CANCELLED);
            classSessionRepository.flush();
            notifier.sessionCancelled(actor.getUserId(), session);
            auditService.log(actor.getUserId(), AuditActions.CANCEL_SESSION, AuditActions.ENTITY_CLASS_SESSION,
                    sessionId, detail(section, session), ipAddress);
            return null;
        });
    }

    /**
     * Tạo lịch hàng tuần: count buổi liên tiếp vào các thứ đã chọn, từ fromDate, không vượt ngày kết thúc học kỳ.
     * Không đủ ngày thì từ chối cả lô. Kiểm tra xung đột cho TẤT CẢ buổi trước khi lưu; có xung đột thì không
     * lưu buổi nào. Trả số buổi đã tạo.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int createWeekly(AppUserPrincipal actor, Long sectionId, WeeklyScheduleForm form, String ipAddress) {
        return guarded(() -> {
            ClassSection section = lockForNewSchedule(actor, sectionId);
            Semester semester = section.getSemester();
            Map<String, String> errors = new LinkedHashMap<>();
            if (form.getDays() == null || form.getDays().isEmpty()) {
                errors.put("days", ScheduleMessages.WEEKDAYS_REQUIRED);
            }
            checkTimes(form.getStartTime(), form.getEndTime(), errors);
            checkInSemester(form.getFromDate(), semester, "fromDate", errors);
            Integer count = form.getCount();
            if (count == null || count < 1 || count > MAX_WEEKLY_SESSIONS) {
                errors.put("count", ScheduleMessages.COUNT_RANGE.formatted(MAX_WEEKLY_SESSIONS));
            }
            Room room = room(form.getRoomId(), errors);
            CatalogSupport.throwIfAny(errors);

            List<LocalDate> dates = weeklyDates(form.getFromDate(), form.getDays(), count, semester.getEndDate());
            if (dates.size() < count) {
                throw new BusinessException(ScheduleMessages.NOT_ENOUGH_DAYS.formatted(dates.size(),
                        semester.getEndDate().format(DATE)), "count");
            }
            List<Slot> slots = dates.stream().map(d -> new Slot(d, form.getStartTime(), form.getEndTime())).toList();
            requireNoConflicts(conflictChecker.checkSessions(section, room, slots, null));

            short next = nextSessionNo(sectionId);
            List<ClassSession> created = new ArrayList<>();
            for (LocalDate date : dates) {
                created.add(newSession(section, room, next++, date, form.getStartTime(), form.getEndTime(),
                        SessionStatus.SCHEDULED));
            }
            classSessionRepository.saveAllAndFlush(created);
            auditService.log(actor.getUserId(), AuditActions.CREATE_WEEKLY_SESSIONS, AuditActions.ENTITY_CLASS_SECTION,
                    sectionId, "section=" + section.getCode() + ", count=" + created.size() + ", days="
                            + form.getDays() + ", from=" + dates.getFirst() + ", to=" + dates.getLast(), ipAddress);
            return created.size();
        });
    }

    /**
     * Các ngày (từ from, tăng dần) rơi vào một trong days, tối đa count ngày, không quá until. Trả ít hơn count
     * ngày nếu tới until mà chưa đủ.
     */
    static List<LocalDate> weeklyDates(LocalDate from, Set<DayOfWeek> days, int count, LocalDate until) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(until) && dates.size() < count; date = date.plusDays(1)) {
            if (days.contains(date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }

    static void checkTimes(LocalTime start, LocalTime end, Map<String, String> errors) {
        if (start == null) {
            errors.putIfAbsent("startTime", "Vui lòng nhập giờ bắt đầu");
        }
        if (end == null) {
            errors.putIfAbsent("endTime", "Vui lòng nhập giờ kết thúc");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            errors.putIfAbsent("endTime", ScheduleMessages.END_AFTER_START);
        }
    }

    /** Ngày phải nằm trong [ngày bắt đầu, ngày kết thúc] của học kỳ. */
    static void checkInSemester(LocalDate date, Semester semester, String field, Map<String, String> errors) {
        if (date == null) {
            errors.putIfAbsent(field, "Vui lòng chọn ngày");
        } else if (date.isBefore(semester.getStartDate()) || date.isAfter(semester.getEndDate())) {
            errors.putIfAbsent(field, ScheduleMessages.OUTSIDE_SEMESTER.formatted(
                    semester.getStartDate().format(DATE), semester.getEndDate().format(DATE)));
        }
    }

    static void requireNoConflicts(List<ScheduleConflict> conflicts) {
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(ScheduleMessages.CONFLICTS, conflicts);
        }
    }

    Room room(Long roomId, Map<String, String> errors) {
        if (roomId == null) {
            return null;
        }
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            errors.put("roomId", ScheduleMessages.ROOM_NOT_FOUND);
        }
        return room;
    }

    /** Lớp trong phạm vi, khóa dòng (UPDLOCK) tới hết transaction. */
    ClassSection lockSection(AppUserPrincipal actor, Long sectionId) {
        classSectionService.findInScope(actor, sectionId);
        return classSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new NotFoundException(SectionMessages.SECTION_NOT_FOUND));
    }

    /** Như lockSection, và lớp không được ở trạng thái CANCELLED. */
    ClassSection lockForNewSchedule(AppUserPrincipal actor, Long sectionId) {
        ClassSection section = lockSection(actor, sectionId);
        if (section.getStatus() == SectionStatus.CANCELLED) {
            throw new BusinessException(ScheduleMessages.SECTION_CANCELLED);
        }
        return section;
    }

    /**
     * Đổi lỗi database thành thông báo: vi phạm ràng buộc qua ConstraintViolationMapper, deadlock của
     * SERIALIZABLE thành "vui lòng thử lại".
     */
    static <T> T guarded(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(ScheduleMessages.CONCURRENT_CHANGE);
        }
    }

    private short nextSessionNo(Long sectionId) {
        Short max = classSessionRepository.findMaxSessionNo(sectionId);
        return (short) (max == null ? 1 : max + 1);
    }

    private ClassSession find(Long sectionId, Long sessionId) {
        return classSessionRepository.findById(sessionId)
                .filter(s -> Objects.equals(s.getClassSection().getId(), sectionId))
                .orElseThrow(() -> new NotFoundException(ScheduleMessages.SESSION_NOT_FOUND));
    }

    private static ClassSession newSession(ClassSection section, Room room, short sessionNo, LocalDate date,
                                           LocalTime start, LocalTime end, SessionStatus status) {
        ClassSession session = new ClassSession();
        session.setClassSection(section);
        session.setRoom(room);
        session.setSessionNo(sessionNo);
        session.setSessionDate(date);
        session.setStartTime(start);
        session.setEndTime(end);
        session.setStatus(status);
        return session;
    }

    private static Long roomId(Room room) {
        return room == null ? null : room.getId();
    }

    private static String detail(ClassSection section, ClassSession session) {
        return "section=" + section.getCode() + ", no=" + session.getSessionNo() + ", date="
                + session.getSessionDate() + ", time=" + session.getStartTime() + "-" + session.getEndTime()
                + ", room=" + (session.getRoom() == null ? "-" : session.getRoom().getCode()) + ", status="
                + session.getStatus();
    }
}
