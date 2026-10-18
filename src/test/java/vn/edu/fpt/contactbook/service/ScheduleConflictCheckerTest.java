package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;
import vn.edu.fpt.contactbook.dto.ScheduleConflict.Type;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Exam;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.ExamType;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.service.ScheduleConflictChecker.Slot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Xung đột lịch theo khoảng thời gian chồng nhau: phòng (buổi học, lịch thi), giảng viên, sinh viên chung,
 * buổi khác của chính lớp, buổi đã hủy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleConflictCheckerTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Mock ClassSessionRepository classSessionRepository;
    @Mock ExamRepository examRepository;
    @Mock EnrollmentRepository enrollmentRepository;

    private ScheduleConflictChecker checker;
    private ClassSection section;
    private ClassSection otherSection;
    private Teacher gv002;
    private Room r101;

    @BeforeEach
    void setUp() {
        checker = new ScheduleConflictChecker(classSessionRepository, examRepository, enrollmentRepository);
        Department se = CatalogTestData.department(1L, "SE");
        Semester fa26 = CatalogTestData.semester(1L, "FA26");
        gv002 = CatalogTestData.teacher(2L, "GV002", se);
        Teacher gv003 = CatalogTestData.teacher(8L, "GV003", se);
        section = CatalogTestData.section(10L, "PRJ301-FA26-01", CatalogTestData.subject(1L, "PRJ301", se), fa26,
                gv002, null, SectionStatus.OPEN);
        otherSection = CatalogTestData.section(27L, "PRN211_FA26_01", CatalogTestData.subject(31L, "PRN211", se),
                fa26, gv003, null, SectionStatus.OPEN);
        r101 = room(1L, "R101");
        when(enrollmentRepository.countSharedStudents(anyLong())).thenReturn(List.of());
        when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                .thenReturn(List.of());
        when(examRepository.findPossibleConflicts(any(), any(), any(), any(), anyCollection())).thenReturn(List.of());
    }

    @ParameterizedTest(name = "[{0}-{1}) và [{2}-{3}) chồng nhau = {4}")
    @CsvSource({
            "07:30, 09:45, 09:45, 12:00, false",
            "09:45, 12:00, 07:30, 09:45, false",
            "07:30, 09:45, 09:00, 10:00, true",
            "09:00, 10:00, 07:30, 09:45, true",
            "07:00, 12:00, 08:00, 09:00, true",
            "08:00, 09:00, 07:00, 12:00, true",
            "07:30, 09:45, 07:30, 09:45, true",
            "07:30, 09:45, 10:00, 11:00, false"
    })
    @DisplayName("Chồng lấn: giáp nhau không tính, chồng một phần và bao trùm có tính")
    void overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd, boolean expected) {
        assertThat(ScheduleConflictChecker.overlaps(aStart, aEnd, bStart, bEnd)).isEqualTo(expected);
    }

    @Nested
    @DisplayName("Buổi học")
    class Sessions {

        @Test
        @DisplayName("Cùng phòng, giờ chồng một phần → xung đột phòng")
        void roomConflictPartialOverlap() {
            ClassSession taken = session(41L, otherSection, r101, "07:30", "09:50", SessionStatus.SCHEDULED);
            whenOthers(taken);

            List<ScheduleConflict> conflicts = check(r101, "09:00", "11:00");

            assertThat(conflicts).extracting(ScheduleConflict::type).containsExactly(Type.ROOM);
            assertThat(conflicts.getFirst().detail()).contains("R101").contains("PRN211_FA26_01").contains("07:30–09:50");
        }

        @Test
        @DisplayName("Giáp nhau (kết thúc đúng lúc bắt đầu) → không xung đột")
        void adjacentIsFine() {
            whenOthers(session(41L, otherSection, r101, "07:30", "09:50", SessionStatus.SCHEDULED));

            assertThat(check(r101, "09:50", "12:00")).isEmpty();
        }

        @Test
        @DisplayName("Cùng giảng viên ở lớp khác, khác phòng → xung đột giảng viên")
        void teacherConflict() {
            ClassSection sameTeacher = CatalogTestData.section(28L, "SWP391_FA26_01", otherSection.getSubject(),
                    section.getSemester(), gv002, null, SectionStatus.OPEN);
            whenOthers(session(50L, sameTeacher, room(14L, "BE-301"), "08:00", "09:00", SessionStatus.SCHEDULED));

            assertThat(check(r101, "07:30", "09:45")).extracting(ScheduleConflict::type).containsExactly(Type.TEACHER);
        }

        @Test
        @DisplayName("Sinh viên đang học cả hai lớp → xung đột sinh viên, kèm số sinh viên chung")
        void studentConflict() {
            when(enrollmentRepository.countSharedStudents(10L)).thenReturn(List.<Object[]>of(new Object[]{27L, 3L}));
            whenOthers(session(41L, otherSection, null, "07:00", "12:00", SessionStatus.SCHEDULED));

            List<ScheduleConflict> conflicts = check(null, "08:00", "09:00");

            assertThat(conflicts).singleElement().satisfies(c -> {
                assertThat(c.type()).isEqualTo(Type.STUDENT);
                assertThat(c.detail()).startsWith("3 sinh viên");
            });
        }

        @Test
        @DisplayName("Một buổi vừa trùng phòng, giảng viên và sinh viên → liệt kê đủ ba xung đột")
        void allTypesTogether() {
            when(enrollmentRepository.countSharedStudents(10L)).thenReturn(List.<Object[]>of(new Object[]{28L, 1L}));
            ClassSection sameTeacher = CatalogTestData.section(28L, "SWP391_FA26_01", otherSection.getSubject(),
                    section.getSemester(), gv002, null, SectionStatus.OPEN);
            whenOthers(session(50L, sameTeacher, r101, "07:30", "09:45", SessionStatus.SCHEDULED));

            assertThat(check(r101, "07:30", "09:45")).extracting(ScheduleConflict::type)
                    .containsExactly(Type.ROOM, Type.TEACHER, Type.STUDENT);
        }

        @Test
        @DisplayName("Lớp có lịch thi, hoặc lớp có chung SV có lịch thi, chồng giờ → xung đột (finding 1)")
        void sessionDuringExamOfOwnOrSharedSection() {
            when(enrollmentRepository.countSharedStudents(10L)).thenReturn(List.<Object[]>of(new Object[]{27L, 2L}));
            Exam ownExam = exam(section, null, "08:00", "09:00");
            Exam sharedExam = exam(otherSection, null, "08:30", "10:00");
            when(examRepository.findPossibleConflicts(eq(DAY), eq(DAY), isNull(), isNull(), anyCollection()))
                    .thenAnswer(invocation -> {
                        java.util.Collection<?> ids = invocation.getArgument(4);
                        return ids.contains(10L) && ids.contains(27L) ? List.of(ownExam, sharedExam) : List.of();
                    });

            assertThat(check(null, "08:15", "09:15")).extracting(ScheduleConflict::type)
                    .containsExactly(Type.SAME_SECTION, Type.STUDENT);
        }

        @Test
        @DisplayName("Phòng đang có lịch thi chồng giờ → xung đột phòng")
        void roomTakenByExam() {
            Exam exam = exam(otherSection, r101, "08:00", "09:30");
            when(examRepository.findPossibleConflicts(eq(DAY), eq(DAY), isNull(), eq(1L), anyCollection()))
                    .thenReturn(List.of(exam));

            assertThat(check(r101, "07:30", "08:30")).singleElement()
                    .satisfies(c -> assertThat(c.detail()).contains("lịch thi"));
        }

        @Test
        @DisplayName("Buổi đã hủy của chính lớp không tính, trừ khi trùng đúng ngày và giờ bắt đầu (uq_sess_cs_slot)")
        void cancelledSessionsOfOwnSection() {
            when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(10L, DAY, DAY)).thenReturn(List.of(
                    session(60L, section, r101, "07:30", "09:45", SessionStatus.CANCELLED)));

            assertThat(check(r101, "08:00", "10:00")).isEmpty();
            assertThat(check(r101, "07:30", "09:00")).extracting(ScheduleConflict::type)
                    .containsExactly(Type.CANCELLED_SLOT);
        }

        @Test
        @DisplayName("Buổi khác của chính lớp chồng giờ → xung đột; buổi đang sửa được bỏ qua")
        void ownSectionOverlapAndExcludedSession() {
            when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(10L, DAY, DAY)).thenReturn(List.of(
                    session(61L, section, null, "07:30", "09:45", SessionStatus.MAKEUP)));

            assertThat(check(null, "09:00", "10:00")).extracting(ScheduleConflict::type)
                    .containsExactly(Type.SAME_SECTION);
            assertThat(checker.checkSessions(section, null, List.of(slot("09:00", "10:00")), 61L)).isEmpty();
        }

        @Test
        @DisplayName("Lịch tuần: mỗi buổi được so với dữ liệu đúng ngày của nó")
        void comparesPerDate() {
            whenOthers(session(41L, otherSection, r101, "07:30", "09:50", SessionStatus.SCHEDULED));
            List<Slot> slots = List.of(new Slot(DAY.minusDays(1), lt("07:30"), lt("09:45")), slot("07:30", "09:45"),
                    new Slot(DAY.plusDays(1), lt("07:30"), lt("09:45")));

            assertThat(checker.checkSessions(section, r101, slots, null)).extracting(ScheduleConflict::date)
                    .containsExactly(DAY);
        }
    }

    @Nested
    @DisplayName("Lịch thi")
    class Exams {

        @Test
        @DisplayName("Sinh viên chung có lịch thi và buổi học chồng giờ → hai xung đột sinh viên")
        void studentExamAndSessionConflicts() {
            when(enrollmentRepository.countSharedStudents(10L)).thenReturn(List.<Object[]>of(new Object[]{27L, 2L}));
            when(examRepository.findPossibleConflicts(eq(DAY), eq(DAY), isNull(), isNull(), anyCollection()))
                    .thenReturn(List.of(exam(otherSection, null, "08:00", "09:30")));
            when(classSessionRepository.findPossibleConflicts(eq(10L), eq(DAY), eq(DAY), isNull(), isNull(),
                    anyCollection())).thenReturn(List.of(session(41L, otherSection, null, "09:00", "11:00",
                    SessionStatus.SCHEDULED)));

            List<ScheduleConflict> conflicts = checker.checkExam(section, null, slot("08:30", "10:00"), null);

            assertThat(conflicts).extracting(ScheduleConflict::type).containsExactly(Type.STUDENT, Type.STUDENT);
        }

        @Test
        @DisplayName("Lớp có buổi học (chưa hủy) trùng giờ thi → xung đột của lớp; buổi đã hủy không tính")
        void ownSectionClassDuringExam() {
            when(classSessionRepository.findByClassSectionIdAndSessionDateBetween(10L, DAY, DAY)).thenReturn(List.of(
                    session(61L, section, null, "07:30", "09:45", SessionStatus.SCHEDULED),
                    session(62L, section, null, "10:00", "12:00", SessionStatus.CANCELLED)));

            assertThat(checker.checkExam(section, null, slot("09:00", "11:00"), null))
                    .extracting(ScheduleConflict::type).containsExactly(Type.SAME_SECTION);
        }
    }

    private List<ScheduleConflict> check(Room room, String start, String end) {
        return checker.checkSessions(section, room, List.of(slot(start, end)), null);
    }

    private void whenOthers(ClassSession... sessions) {
        when(classSessionRepository.findPossibleConflicts(anyLong(), any(), any(), any(), any(), anyCollection()))
                .thenReturn(List.of(sessions));
    }

    private static Slot slot(String start, String end) {
        return new Slot(DAY, lt(start), lt(end));
    }

    private static LocalTime lt(String time) {
        return LocalTime.parse(time);
    }

    private static Room room(Long id, String code) {
        Room room = new Room();
        room.setId(id);
        room.setCode(code);
        return room;
    }

    private static ClassSession session(Long id, ClassSection section, Room room, String start, String end,
                                        SessionStatus status) {
        ClassSession session = new ClassSession();
        session.setId(id);
        session.setClassSection(section);
        session.setRoom(room);
        session.setSessionNo((short) (id % 100));
        session.setSessionDate(DAY);
        session.setStartTime(lt(start));
        session.setEndTime(lt(end));
        session.setStatus(status);
        return session;
    }

    private static Exam exam(ClassSection section, Room room, String start, String end) {
        Exam exam = new Exam();
        exam.setId(90L);
        exam.setClassSection(section);
        exam.setRoom(room);
        exam.setExamType(ExamType.FINAL);
        exam.setExamDate(DAY);
        exam.setStartTime(lt(start));
        exam.setEndTime(lt(end));
        return exam;
    }
}
