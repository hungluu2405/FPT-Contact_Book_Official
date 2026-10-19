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
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.dto.AttendanceSaveResult;
import vn.edu.fpt.contactbook.dto.AttendanceSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.SessionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.ClassSessionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Điểm danh một buổi: phạm vi giảng viên / trưởng bộ môn / ADMIN, sessionId phải thuộc lớp, từ chối buổi hủy, buổi
 * tương lai, lớp hủy; lần đầu không cần lý do; điều chỉnh bắt buộc lý do và audit cũ → mới cho từng sinh viên.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    /** users.id 3 là giảng viên GV002 (teachers.id 2), dạy lớp 10. */
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    /** users.id 16 là giảng viên GV003 (teachers.id 8), không dạy lớp 10. */
    static final AppUserPrincipal OTHER_TEACHER = CatalogTestData.principal(16L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal HEAD_IA = CatalogTestData.principal(15L,
            Set.of(RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(38L)));

    @Mock ClassSectionRepository classSectionRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock NotificationService notificationService;
    @Mock NotificationAudience audience;
    @Mock UserRequestRepository userRequestRepository;

    private AttendanceService service;
    private ClassSection section;
    private ClassSession session;
    private Student s1;
    private Student s2;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        UserAccessPolicy userAccessPolicy = new UserAccessPolicy(teacherRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(userAccessPolicy);
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        AttendanceScope scope = new AttendanceScope(teacherRepository, classSectionRepository, classSectionService,
                userAccessPolicy);
        service = new AttendanceService(scope, classSectionRepository, classSessionRepository, enrollmentRepository,
                attendanceRepository, userRepository, auditService, new AttendanceRule(20), notificationService,
                audience, userRequestRepository);

        Department se = CatalogTestData.department(1L, "SE");
        Semester fa26 = CatalogTestData.semester(1L, "FA26");
        Teacher gv002 = CatalogTestData.teacher(2L, "GV002", se);
        section = CatalogTestData.section(10L, "PRJ301-FA26-01", CatalogTestData.subject(1L, "PRJ301", se), fa26,
                gv002, 40, SectionStatus.OPEN);
        session = session(100L, section, AppTime.today().minusDays(1), SessionStatus.SCHEDULED);
        s1 = CatalogTestData.student(1L, "SE190001", StudentStatus.STUDYING);
        s2 = CatalogTestData.student(2L, "SE190002", StudentStatus.STUDYING);

        when(teacherRepository.findByUserId(3L)).thenReturn(Optional.of(gv002));
        when(teacherRepository.findByUserId(16L)).thenReturn(Optional.of(CatalogTestData.teacher(8L, "GV003", se)));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(classSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(10L,
                EnrollmentStatus.ENROLLED)).thenReturn(List.of(
                CatalogTestData.enrollment(1L, section, s1, EnrollmentStatus.ENROLLED),
                CatalogTestData.enrollment(2L, section, s2, EnrollmentStatus.ENROLLED)));
        when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of());
        when(userRepository.getReferenceById(anyLong())).thenAnswer(invocation -> {
            User user = new User();
            user.setId(invocation.getArgument(0));
            return user;
        });
    }

    @Nested
    @DisplayName("Phạm vi và điều kiện buổi học")
    class Scope {

        @Test
        @DisplayName("Giảng viên của lớp mở được bảng điểm danh: mọi sinh viên đang học, chưa có trạng thái")
        void teacherOpensOwnSheet() {
            AttendanceSheet sheet = service.sheet(TEACHER, Access.TEACHER, 10L, 100L);

            assertThat(sheet.marked()).isFalse();
            assertThat(sheet.rows()).extracting(AttendanceSheet.Row::studentCode, AttendanceSheet.Row::status)
                    .containsExactly(tuple("SE190001", null), tuple("SE190002", null));
        }

        @Test
        @DisplayName("Giảng viên khác (gõ URL) bị chặn 403 khi xem và khi lưu, không ghi gì")
        void otherTeacherIsDenied() {
            assertThatThrownBy(() -> service.sheet(OTHER_TEACHER, Access.TEACHER, 10L, 100L))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.save(OTHER_TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.PRESENT), null), "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
            verify(attendanceRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Lớp không tồn tại cũng là 403 với giảng viên, để không dò được id")
        void missingSectionIsDeniedForTeacher() {
            when(classSectionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.sheet(TEACHER, Access.TEACHER, 99L, 100L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Người không có hồ sơ giảng viên bị 403")
        void noTeacherProfileIsDenied() {
            AppUserPrincipal headOnly = CatalogTestData.principal(15L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT), Map.of());

            assertThatThrownBy(() -> service.sheet(headOnly, Access.TEACHER, 10L, 100L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("sessionId thuộc lớp khác → không tìm thấy buổi học")
        void sessionOfAnotherSection() {
            ClassSection other = CatalogTestData.section(11L, "OTHER", section.getSubject(), section.getSemester(),
                    section.getTeacher(), 40, SectionStatus.OPEN);
            when(classSessionRepository.findById(200L)).thenReturn(Optional.of(
                    session(200L, other, AppTime.today(), SessionStatus.SCHEDULED)));

            assertThatThrownBy(() -> service.sheet(TEACHER, Access.TEACHER, 10L, 200L))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 200L, allPresent(), "127.0.0.1"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Buổi đã hủy không điểm danh được")
        void cancelledSession() {
            session.setStatus(SessionStatus.CANCELLED);

            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L, allPresent(), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.SESSION_CANCELLED);
            verify(attendanceRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Buổi ngày mai (chưa diễn ra) không điểm danh được")
        void futureSession() {
            session.setSessionDate(AppTime.today().plusDays(1));

            assertThatThrownBy(() -> service.sheet(TEACHER, Access.TEACHER, 10L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageStartingWith("Buổi học chưa diễn ra");
            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L, allPresent(), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Buổi đúng hôm nay điểm danh được")
        void todaySessionIsAllowed() {
            session.setSessionDate(AppTime.today());

            assertThat(service.save(TEACHER, Access.TEACHER, 10L, 100L, allPresent(), "127.0.0.1").firstTime())
                    .isTrue();
        }

        @Test
        @DisplayName("Lớp học phần đã hủy không điểm danh được")
        void cancelledSection() {
            section.setStatus(SectionStatus.CANCELLED);

            assertThatThrownBy(() -> service.sheet(TEACHER, Access.TEACHER, 10L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.SECTION_CANCELLED);
        }

        @Test
        @DisplayName("Trưởng bộ môn xem được lớp thuộc bộ môn mình nhưng không lưu được (403)")
        void headCanViewButNotSave() {
            when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of(row(s1, AttendanceStatus.PRESENT)));

            assertThat(service.sheet(HEAD_SE, Access.MANAGER, 10L, 100L).marked()).isTrue();
            assertThatThrownBy(() -> service.save(HEAD_SE, Access.MANAGER, 10L, 100L, allPresent(), "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Trưởng bộ môn khác không xem được lớp ngoài bộ môn (403)")
        void headOfOtherDepartmentIsDenied() {
            assertThatThrownBy(() -> service.sheet(HEAD_IA, Access.MANAGER, 10L, 100L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("ADMIN không điểm danh lần đầu thay giảng viên")
        void adminCannotMarkFirstTime() {
            assertThatThrownBy(() -> service.save(ADMIN, Access.MANAGER, 10L, 100L, allPresent(), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.NOT_MARKED_YET);
        }
    }

    @Nested
    @DisplayName("Lưu lần đầu")
    class FirstTime {

        @Test
        @DisplayName("Tạo một dòng cho mỗi sinh viên, marked_by là người đăng nhập, không cần lý do, ghi một audit")
        void createsRowsAndAudit() {
            AttendanceSaveResult result = service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.LATE), null), "10.0.0.1");

            assertThat(result).isEqualTo(new AttendanceSaveResult(true, 2));
            ArgumentCaptor<List<Attendance>> saved = ArgumentCaptor.forClass(List.class);
            verify(attendanceRepository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(a -> a.getStudent().getStudentCode(), Attendance::getStatus,
                            a -> a.getMarkedBy().getId(), Attendance::getNote, Attendance::getSession)
                    .containsExactly(tuple("SE190001", AttendanceStatus.PRESENT, 3L, null, session),
                            tuple("SE190002", AttendanceStatus.LATE, 3L, null, session));
            AuditLog audit = singleAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.MARK_ATTENDANCE);
            assertThat(audit.getEntityId()).isEqualTo(100L);
            assertThat(audit.getDetail()).contains("PRESENT=1", "LATE=1", "ABSENT_UNEXCUSED=0");
            assertThat(audit.getIpAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("Thiếu trạng thái của một sinh viên → yêu cầu chọn đủ, không ghi gì")
        void missingStatus() {
            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.PRESENT), null), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.STATUS_MISSING);
            verify(attendanceRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Có sinh viên không còn trong lớp (trang cũ) → báo danh sách đã thay đổi")
        void unknownStudent() {
            Map<Long, AttendanceStatus> statuses = new HashMap<>(Map.of(1L, AttendanceStatus.PRESENT,
                    2L, AttendanceStatus.PRESENT, 77L, AttendanceStatus.PRESENT));

            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L, form(statuses, null),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.STUDENTS_CHANGED);
        }

        @Test
        @DisplayName("Lớp chưa có sinh viên đang học → báo lỗi")
        void noStudents() {
            when(enrollmentRepository.findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(10L,
                    EnrollmentStatus.ENROLLED)).thenReturn(List.of());

            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L, form(Map.of(), null),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.NO_STUDENTS);
        }
    }

    @Nested
    @DisplayName("Điều chỉnh")
    class Adjust {

        private Attendance row1;
        private Attendance row2;

        @BeforeEach
        void marked() {
            row1 = row(s1, AttendanceStatus.PRESENT);
            row1.setNote("Đúng giờ");
            row2 = row(s2, AttendanceStatus.PRESENT);
            when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of(row1, row2));
        }

        @Test
        @DisplayName("Đổi trạng thái mà thiếu lý do → lỗi ở ô lý do, không đổi gì")
        void reasonRequired() {
            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.ABSENT_EXCUSED, 2L, AttendanceStatus.PRESENT), "   "),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.REASON_REQUIRED)
                    .extracting(e -> ((BusinessException) e).getField()).isEqualTo("reason");
            assertThat(row1.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lý do dài hơn 255 ký tự bị từ chối")
        void reasonTooLong() {
            assertThatThrownBy(() -> service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.LATE, 2L, AttendanceStatus.PRESENT), "x".repeat(256)),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(AttendanceMessages.REASON_SIZE);
        }

        @Test
        @DisplayName("Chỉ dòng bị đổi được cập nhật (lý do vào note, marked_by là người sửa) và audit cũ → mới")
        void adjustsChangedRowsOnly() {
            AttendanceSaveResult result = service.save(ADMIN, Access.MANAGER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.ABSENT_EXCUSED, 2L, AttendanceStatus.PRESENT),
                            " Có giấy khám bệnh "), "127.0.0.1");

            assertThat(result).isEqualTo(new AttendanceSaveResult(false, 1));
            assertThat(row1.getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
            assertThat(row1.getNote()).isEqualTo("Có giấy khám bệnh");
            assertThat(row1.getMarkedBy().getId()).isEqualTo(1L);
            assertThat(row2.getNote()).isNull();
            assertThat(row2.getMarkedBy().getId()).isEqualTo(3L);
            AuditLog audit = singleAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ADJUST_ATTENDANCE);
            assertThat(audit.getEntityType()).isEqualTo(AuditActions.ENTITY_ATTENDANCE);
            assertThat(audit.getEntityId()).isEqualTo(501L);
            assertThat(audit.getDetail()).contains("student=SE190001", "status=PRESENT -> ABSENT_EXCUSED",
                    "oldNote=Đúng giờ", "reason=Có giấy khám bệnh");
        }

        @Test
        @DisplayName("Sinh viên ghi danh sau lần lưu đầu được tạo dòng mới, audit trạng thái cũ là '-'")
        void studentAddedLater() {
            when(attendanceRepository.findBySessionId(100L)).thenReturn(List.of(row1));

            AttendanceSaveResult result = service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.LATE), "Bổ sung sinh viên mới"),
                    "127.0.0.1");

            assertThat(result.changed()).isEqualTo(1);
            ArgumentCaptor<List<Attendance>> saved = ArgumentCaptor.forClass(List.class);
            verify(attendanceRepository).saveAllAndFlush(saved.capture());
            assertThat(saved.getValue()).singleElement().satisfies(a -> {
                assertThat(a.getStudent()).isSameAs(s2);
                assertThat(a.getStatus()).isEqualTo(AttendanceStatus.LATE);
            });
            assertThat(singleAudit().getDetail()).contains("status=- -> LATE");
        }

        @Test
        @DisplayName("Không đổi gì → không cần lý do, không ghi, không audit")
        void noChanges() {
            AttendanceSaveResult result = service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.PRESENT), null), "127.0.0.1");

            assertThat(result).isEqualTo(new AttendanceSaveResult(false, 0));
            verify(attendanceRepository, never()).saveAllAndFlush(anyList());
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Mỗi sinh viên bị đổi một dòng audit")
        void oneAuditPerChangedStudent() {
            service.save(TEACHER, Access.TEACHER, 10L, 100L,
                    form(Map.of(1L, AttendanceStatus.LATE, 2L, AttendanceStatus.ABSENT_UNEXCUSED), "Nhập nhầm"),
                    "127.0.0.1");

            verify(auditLogRepository, times(2)).save(any(AuditLog.class));
        }
    }

    @Nested
    @DisplayName("Cảnh báo vắng vượt ngưỡng (20%, 10 buổi chưa hủy)")
    class AbsenceWarning {

        @BeforeEach
        void tenSessions() {
            s1.getUser().setId(44L);
            when(classSessionRepository.countByClassSectionIdAndStatusNot(10L, SessionStatus.CANCELLED))
                    .thenReturn(10L);
            when(audience.studentsAndParents(List.of(1L))).thenReturn(Set.of(44L, 55L));
        }

        @Test
        @DisplayName("Trước 2/10 (20%, chưa cảnh báo), sau 3/10 (30%) → gửi SV và PH một thông báo")
        void notifiesWhenCrossing() {
            when(attendanceRepository.countByStudentAndStatus(anyLong(), anyCollection())).thenReturn(
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_UNEXCUSED, 2L}),
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_UNEXCUSED, 3L}));

            service.save(TEACHER, Access.TEACHER, 10L, 100L, form(Map.of(1L, AttendanceStatus.ABSENT_UNEXCUSED,
                    2L, AttendanceStatus.PRESENT), null), "127.0.0.1");

            verify(notificationService).sendAutomatic(eq(3L), contains("Cảnh báo vắng vượt 20%"),
                    contains("đã vắng 3/10 buổi (30,0%)"), eq(NotificationPriority.URGENT),
                    eq(NotificationTargetType.USER), eq(44L), eq(Set.of(44L, 55L)));
        }

        @Test
        @DisplayName("Đã vượt từ trước (3/10 → 4/10) → không gửi lặp")
        void noRepeatWhenAlreadyWarned() {
            when(attendanceRepository.countByStudentAndStatus(anyLong(), anyCollection())).thenReturn(
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_EXCUSED, 3L}),
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_EXCUSED, 4L}));

            service.save(TEACHER, Access.TEACHER, 10L, 100L, form(Map.of(1L, AttendanceStatus.ABSENT_EXCUSED,
                    2L, AttendanceStatus.PRESENT), null), "127.0.0.1");

            verify(notificationService, never()).sendAutomatic(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Sau lần lưu vừa đúng ngưỡng (1/10 → 2/10 = 20%) → chưa gửi")
        void noNotificationAtThreshold() {
            when(attendanceRepository.countByStudentAndStatus(anyLong(), anyCollection())).thenReturn(
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_UNEXCUSED, 1L}),
                    List.<Object[]>of(new Object[]{1L, AttendanceStatus.ABSENT_UNEXCUSED, 2L}));

            service.save(TEACHER, Access.TEACHER, 10L, 100L, form(Map.of(1L, AttendanceStatus.ABSENT_UNEXCUSED,
                    2L, AttendanceStatus.PRESENT), null), "127.0.0.1");

            verify(notificationService, never()).sendAutomatic(any(), any(), any(), any(), any(), any(), any());
        }
    }

    private AuditLog singleAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private Attendance row(Student student, AttendanceStatus status) {
        Attendance row = new Attendance();
        row.setId(500L + student.getId());
        row.setSession(session);
        row.setStudent(student);
        row.setStatus(status);
        User marker = new User();
        marker.setId(3L);
        row.setMarkedBy(marker);
        return row;
    }

    private static AttendanceForm allPresent() {
        return form(Map.of(1L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.PRESENT), null);
    }

    private static AttendanceForm form(Map<Long, AttendanceStatus> statuses, String reason) {
        AttendanceForm form = new AttendanceForm();
        form.setStatuses(new LinkedHashMap<>(statuses));
        form.setReason(reason);
        return form;
    }

    private static ClassSession session(Long id, ClassSection section, LocalDate date, SessionStatus status) {
        ClassSession session = new ClassSession();
        session.setId(id);
        session.setClassSection(section);
        session.setSessionNo((short) 1);
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(7, 30));
        session.setEndTime(LocalTime.of(9, 45));
        session.setStatus(status);
        return session;
    }
}
