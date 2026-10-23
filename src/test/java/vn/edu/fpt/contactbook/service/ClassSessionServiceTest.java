package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;
import vn.edu.fpt.contactbook.dto.SessionForm;
import vn.edu.fpt.contactbook.dto.WeeklyScheduleForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.repository.RoomRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Buổi học: sinh lịch tuần, từ chối cả lô khi không đủ ngày hoặc có xung đột, ngoài học kỳ, buổi bù, sửa buổi
 * đã điểm danh, hủy, phạm vi trưởng bộ môn, gọi ScheduleChangeNotifier.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassSessionServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock ClassSessionRepository classSessionRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock RoomRepository roomRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock ExamRepository examRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private ClassSessionService service;
    private ScheduleChangeNotifier notifier;
    private ClassSection section;
    private Room r101;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(new UserAccessPolicy(teacherRepository));
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        notifier = mock(ScheduleChangeNotifier.class);
        ScheduleConflictChecker checker = new ScheduleConflictChecker(classSessionRepository, examRepository,
                enrollmentRepository);
        service = new ClassSessionService(classSessionRepository, classSectionRepository, roomRepository,
                attendanceRepository, classSectionService, checker, notifier, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        Semester hk1 = CatalogTestData.semester(1L, "HK1-2627");
        hk1.setStartDate(LocalDate.of(2026, 9, 7));
        hk1.setEndDate(LocalDate.of(2026, 12, 20));
        section = CatalogTestData.section(10L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se), hk1,
                CatalogTestData.teacher(2L, "GV002", se), null, SectionStatus.OPEN);
        r101 = new Room();
        r101.setId(1L);
        r101.setCode("R101");
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(r101));
        when(classSessionRepository.findMaxSessionNo(10L)).thenReturn((short) 4);
        when(enrollmentRepository.countSharedStudents(anyLong())).thenReturn(List.of());
        when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                .thenReturn(List.of());
        when(examRepository.findPossibleConflicts(any(), any(), any(), any(), anyCollection())).thenReturn(List.of());
        when(classSessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ClassSession saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
    }

    @Nested
    @DisplayName("Sinh ngày lịch tuần")
    class WeeklyDates {

        @Test
        @DisplayName("Thứ Hai và thứ Tư từ thứ Ba 08/09/2026: 4 buổi đúng thứ, tăng dần")
        void picksRequestedWeekdays() {
            List<LocalDate> dates = ClassSessionService.weeklyDates(LocalDate.of(2026, 9, 8),
                    EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 4, LocalDate.of(2026, 12, 20));

            assertThat(dates).containsExactly(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 21));
        }

        @Test
        @DisplayName("Không vượt ngày kết thúc: chỉ trả các ngày tới until (tính cả until)")
        void stopsAtUntil() {
            List<LocalDate> dates = ClassSessionService.weeklyDates(LocalDate.of(2026, 12, 7),
                    EnumSet.of(DayOfWeek.MONDAY), 10, LocalDate.of(2026, 12, 21));

            assertThat(dates).containsExactly(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 14),
                    LocalDate.of(2026, 12, 21));
        }
    }

    @Nested
    @DisplayName("Tạo lịch hàng tuần")
    class Weekly {

        @Test
        @DisplayName("Đủ chỗ, không xung đột: tạo đúng số buổi SCHEDULED, session_no nối tiếp, ghi audit")
        void createsSessions() {
            int created = service.createWeekly(ADMIN, 10L, weekly(LocalDate.of(2026, 9, 7), 6), "127.0.0.1");

            assertThat(created).isEqualTo(6);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ClassSession>> saved = ArgumentCaptor.forClass(List.class);
            verify(classSessionRepository).saveAllAndFlush(saved.capture());
            assertThat(saved.getValue()).extracting(ClassSession::getSessionNo)
                    .containsExactly((short) 5, (short) 6, (short) 7, (short) 8, (short) 9, (short) 10);
            assertThat(saved.getValue()).allSatisfy(s -> {
                assertThat(s.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
                assertThat(s.getSessionDate().getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
                assertThat(s.getRoom()).isSameAs(r101);
            });
            verify(auditLogRepository).save(any());
        }

        @Test
        @DisplayName("Không đủ ngày tới cuối học kỳ → từ chối cả lô, báo số buổi tối đa")
        void notEnoughDaysRejectsAll() {
            assertThatThrownBy(() -> service.createWeekly(ADMIN, 10L, weekly(LocalDate.of(2026, 12, 7), 10),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tối đa 4 buổi").hasMessageContaining("20/12/2026");
            verify(classSessionRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("Một buổi trong lịch tuần trùng phòng → không lưu buổi nào, liệt kê buổi bị trùng")
        void oneConflictSavesNothing() {
            ClassSection other = CatalogTestData.section(27L, "TIN11-HK1-2627-01", section.getSubject(),
                    section.getSemester(), CatalogTestData.teacher(8L, "GV003", section.getSubject().getDepartment()),
                    null, SectionStatus.OPEN);
            ClassSession taken = new ClassSession();
            taken.setId(41L);
            taken.setClassSection(other);
            taken.setRoom(r101);
            taken.setSessionDate(LocalDate.of(2026, 9, 17));
            taken.setStartTime(LocalTime.of(8, 0));
            taken.setEndTime(LocalTime.of(9, 0));
            taken.setStatus(SessionStatus.SCHEDULED);
            when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                    .thenReturn(List.of(taken));

            assertThatThrownBy(() -> service.createWeekly(ADMIN, 10L, weekly(LocalDate.of(2026, 9, 7), 6),
                    "127.0.0.1"))
                    .isInstanceOfSatisfying(ScheduleConflictException.class, e -> assertThat(e.getConflicts())
                            .singleElement()
                            .satisfies(c -> {
                                assertThat(c.date()).isEqualTo(LocalDate.of(2026, 9, 17));
                                assertThat(c.type()).isEqualTo(ScheduleConflict.Type.ROOM);
                            }));
            verify(classSessionRepository, never()).saveAllAndFlush(anyList());
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        void validatesDaysCountAndSemesterStart() {
            WeeklyScheduleForm form = weekly(LocalDate.of(2026, 9, 1), 0);
            form.getDays().clear();

            assertThatThrownBy(() -> service.createWeekly(ADMIN, 10L, form, "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getFieldErrors()).containsKeys("days", "fromDate", "count"));
        }
    }

    @Nested
    @DisplayName("Buổi lẻ, sửa, hủy")
    class Single {

        @Test
        @DisplayName("Buổi thêm lẻ là MAKEUP, session_no = lớn nhất + 1, báo thêm buổi bù")
        void createMakeup() {
            service.create(ADMIN, 10L, single(LocalDate.of(2026, 10, 24), "13:00", "15:15", 1L), "127.0.0.1");

            ArgumentCaptor<ClassSession> saved = ArgumentCaptor.forClass(ClassSession.class);
            verify(classSessionRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(SessionStatus.MAKEUP);
            assertThat(saved.getValue().getSessionNo()).isEqualTo((short) 5);
            verify(notifier).makeupSessionAdded(1L, saved.getValue());
        }

        @Test
        @DisplayName("Ngày ngoài học kỳ, giờ kết thúc không sau giờ bắt đầu → lỗi cạnh ô")
        void outsideSemesterAndBadTimes() {
            assertThatThrownBy(() -> service.create(ADMIN, 10L, single(LocalDate.of(2026, 12, 21), "09:00", "09:00",
                    null), "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("endTime", ScheduleMessages.END_AFTER_START)
                            .containsKey("sessionDate"));
            assertThatThrownBy(() -> service.create(ADMIN, 10L, single(LocalDate.of(2026, 9, 6), "07:30", "09:00",
                    null), "127.0.0.1"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getFieldErrors().get("sessionDate")).contains("07/09/2026"));
            verify(classSessionRepository, never()).saveAndFlush(any());
        }

        @Test
        void cancelledSectionRejectsNewSessions() {
            section.setStatus(SectionStatus.CANCELLED);

            assertThatThrownBy(() -> service.create(ADMIN, 10L, single(LocalDate.of(2026, 10, 24), "13:00", "15:15",
                    null), "127.0.0.1")).hasMessage(ScheduleMessages.SECTION_CANCELLED);
        }

        @Test
        @DisplayName("Buổi đã có điểm danh: đổi giờ bị chặn; chỉ đổi phòng và ghi chú thì được, báo đổi phòng")
        void attendedSessionOnlyRoomAndNote() {
            ClassSession attended = existing(SessionStatus.DONE);
            when(attendanceRepository.existsBySessionId(41L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(ADMIN, 10L, 41L, single(attended.getSessionDate(), "08:00",
                    "09:45", null), "127.0.0.1")).hasMessage(ScheduleMessages.HAS_ATTENDANCE_TIME);
            assertThat(attended.getStartTime()).isEqualTo(LocalTime.of(7, 30));

            SessionForm roomOnly = single(attended.getSessionDate(), "07:30", "09:45", 1L);
            roomOnly.setNote("Đổi phòng");
            service.update(ADMIN, 10L, 41L, roomOnly, "127.0.0.1");
            assertThat(attended.getRoom()).isSameAs(r101);
            assertThat(attended.getNote()).isEqualTo("Đổi phòng");
            verify(notifier).sessionRoomChanged(1L, attended, null);
            verify(notifier, never()).sessionRescheduled(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Chỉ đổi phòng: chỉ kiểm tra trùng phòng, không chặn vì trùng giáo viên có sẵn (finding 3)")
        void roomOnlyChangeChecksRoomOnly() {
            ClassSession session = existing(SessionStatus.DONE);
            when(attendanceRepository.existsBySessionId(41L)).thenReturn(true);
            ClassSession sameTeacher = new ClassSession();
            sameTeacher.setId(77L);
            sameTeacher.setClassSection(CatalogTestData.section(28L, "STEM-HK1-2627-01", section.getSubject(),
                    section.getSemester(), section.getTeacher(), null, SectionStatus.OPEN));
            sameTeacher.setSessionDate(session.getSessionDate());
            sameTeacher.setStartTime(LocalTime.of(8, 0));
            sameTeacher.setEndTime(LocalTime.of(9, 0));
            sameTeacher.setStatus(SessionStatus.SCHEDULED);
            when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                    .thenAnswer(invocation -> invocation.getArgument(4) == null ? List.of() : List.of(sameTeacher));

            service.update(ADMIN, 10L, 41L, single(session.getSessionDate(), "07:30", "09:45", 1L), "127.0.0.1");

            assertThat(session.getRoom()).isSameAs(r101);
        }

        @Test
        @DisplayName("Đổi giờ (kèm đổi phòng) buổi chưa điểm danh → MỘT thông báo đổi giờ kèm giờ cũ, không báo đổi phòng riêng")
        void rescheduleNotifies() {
            ClassSession session = existing(SessionStatus.SCHEDULED);

            service.update(ADMIN, 10L, 41L, single(session.getSessionDate(), "13:00", "15:15", 1L), "127.0.0.1");

            verify(notifier).sessionRescheduled(1L, session, LocalDate.of(2026, 10, 19), LocalTime.of(7, 30),
                    LocalTime.of(9, 45));
            verify(notifier, never()).sessionRoomChanged(any(), any(), any());
        }

        @Test
        @DisplayName("Hủy: đổi sang CANCELLED, không xóa, báo hủy; buổi đã điểm danh hoặc đã hủy thì không hủy được")
        void cancel() {
            ClassSession session = existing(SessionStatus.SCHEDULED);

            service.cancel(ADMIN, 10L, 41L, "127.0.0.1");

            assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
            verify(classSessionRepository, never()).delete(any());
            verify(notifier).sessionCancelled(1L, session);
            assertThatThrownBy(() -> service.cancel(ADMIN, 10L, 41L, "127.0.0.1"))
                    .hasMessage(ScheduleMessages.SESSION_ALREADY_CANCELLED);

            ClassSession attended = existing(SessionStatus.DONE);
            when(attendanceRepository.existsBySessionId(41L)).thenReturn(true);
            assertThatThrownBy(() -> service.cancel(ADMIN, 10L, 41L, "127.0.0.1"))
                    .hasMessage(ScheduleMessages.HAS_ATTENDANCE_CANCEL);
            assertThat(attended.getStatus()).isEqualTo(SessionStatus.DONE);
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn SE với lớp của tổ chuyên môn khác → 403, không lưu")
        void headOutsideScope() {
            Department ia = CatalogTestData.department(38L, "IA");
            section.setSubject(CatalogTestData.subject(50L, "IAO201", ia));

            assertThatThrownBy(() -> service.create(HEAD_SE, 10L, single(LocalDate.of(2026, 10, 24), "13:00",
                    "15:15", null), "127.0.0.1")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.createWeekly(HEAD_SE, 10L, weekly(LocalDate.of(2026, 9, 7), 2),
                    "127.0.0.1")).isInstanceOf(AccessDeniedException.class);
            verify(classSessionRepository, never()).saveAndFlush(any());
        }
    }

    private ClassSession existing(SessionStatus status) {
        ClassSession session = new ClassSession();
        session.setId(41L);
        session.setClassSection(section);
        session.setSessionNo((short) 1);
        session.setSessionDate(LocalDate.of(2026, 10, 19));
        session.setStartTime(LocalTime.of(7, 30));
        session.setEndTime(LocalTime.of(9, 45));
        session.setStatus(status);
        when(classSessionRepository.findById(41L)).thenReturn(Optional.of(session));
        return session;
    }

    private static WeeklyScheduleForm weekly(LocalDate from, int count) {
        WeeklyScheduleForm form = new WeeklyScheduleForm();
        form.getDays().addAll(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));
        form.setStartTime(LocalTime.of(7, 30));
        form.setEndTime(LocalTime.of(9, 45));
        form.setRoomId(1L);
        form.setFromDate(from);
        form.setCount(count);
        return form;
    }

    private static SessionForm single(LocalDate date, String start, String end, Long roomId) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        form.setStartTime(LocalTime.parse(start));
        form.setEndTime(LocalTime.parse(end));
        form.setRoomId(roomId);
        return form;
    }
}
