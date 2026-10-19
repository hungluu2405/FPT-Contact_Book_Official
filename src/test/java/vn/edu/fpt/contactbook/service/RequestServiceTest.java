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
import vn.edu.fpt.contactbook.dto.LeaveRequestForm;
import vn.edu.fpt.contactbook.dto.OtherRequestForm;
import vn.edu.fpt.contactbook.dto.ReviewResult;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.Attendance;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.RequestFile;
import vn.edu.fpt.contactbook.model.RequestReview;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.StoredFile;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AttendanceRepository;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.NotificationAudienceRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.RequestFileRepository;
import vn.edu.fpt.contactbook.repository.RequestReviewRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.StoredFileRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRequestRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.RequestService.View;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;
import vn.edu.fpt.contactbook.util.AppTime;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đơn từ: tạo đơn (ngày, lớp đang học, báo người duyệt), hủy chỉ khi PENDING và chỉ người gửi, duyệt / từ chối chỉ
 * người duyệt của đơn, duyệt đơn nghỉ chuyển đúng các dòng vắng không phép do truy vấn trả về và ghi audit từng dòng,
 * đơn không còn PENDING (đã hủy, đã duyệt) thì từ chối thao tác.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestServiceTest {

    /** users.id 3: giảng viên GV002 (teachers.id 2) dạy lớp 10. */
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    /** users.id 16: giảng viên GV003 (teachers.id 8), chủ nhiệm lớp hành chính của sinh viên. */
    static final AppUserPrincipal HOMEROOM = CatalogTestData.principal(16L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    /** users.id 4: sinh viên SE190001 (students.id 1). */
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    /** users.id 5: phụ huynh (parents.id 1) đã liên kết SE190001. */
    static final AppUserPrincipal PARENT = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());
    static final AppUserPrincipal OUTSIDER = CatalogTestData.principal(18L, Set.of(RoleCodes.STUDENT), Map.of());

    @Mock UserRequestRepository requestRepository;
    @Mock RequestReviewRepository reviewRepository;
    @Mock RequestFileRepository requestFileRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock StudentRepository studentRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock NotificationAudienceRepository audienceRepository;
    @Mock ScheduleViewService scheduleViewService;
    @Mock AttachmentStorage attachmentStorage;
    @Mock NotificationService notificationService;
    @Mock AuditLogRepository auditLogRepository;

    private RequestService service;
    private ClassSection section;
    private Student student;
    private User studentUser;
    private User parentUser;

    @BeforeEach
    void setUp() {
        service = new RequestService(requestRepository, reviewRepository, requestFileRepository,
                storedFileRepository, studentRepository, teacherRepository, parentRepository,
                parentStudentRepository, enrollmentRepository, attendanceRepository, userRepository, roleRepository,
                audienceRepository, scheduleViewService, new RequestReviewerResolver(), attachmentStorage,
                notificationService, new AuditService(auditLogRepository, userRepository));

        Department se = CatalogTestData.department(1L, "SE");
        Teacher gv002 = CatalogTestData.teacher(2L, "GV002", se);
        gv002.getUser().setId(3L);
        Teacher gv003 = CatalogTestData.teacher(8L, "GV003", se);
        gv003.getUser().setId(16L);
        section = CatalogTestData.section(10L, "PRJ301-FA26-01", CatalogTestData.subject(1L, "PRJ301", se),
                CatalogTestData.semester(1L, "FA26"), gv002, 40, SectionStatus.OPEN);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setHomeroomTeacher(gv003);
        student = CatalogTestData.student(1L, "SE190001", StudentStatus.STUDYING);
        student.setSchoolClass(schoolClass);
        studentUser = student.getUser();
        studentUser.setId(4L);
        parentUser = new User();
        parentUser.setId(5L);
        parentUser.setFullName("Phụ huynh");

        when(scheduleViewService.studentId(STUDENT, Audience.STUDENT, null)).thenReturn(1L);
        when(scheduleViewService.studentId(PARENT, Audience.PARENT, 1L)).thenReturn(1L);
        when(scheduleViewService.studentId(PARENT, Audience.PARENT, 2L))
                .thenThrow(new AccessDeniedException("Student is not linked to this parent"));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.getReferenceById(4L)).thenReturn(studentUser);
        when(userRepository.getReferenceById(5L)).thenReturn(parentUser);
        when(audienceRepository.activeAmong(anyCollection()))
                .thenAnswer(inv -> List.copyOf((Collection<Long>) inv.getArgument(0)));
        when(requestRepository.save(any(UserRequest.class))).thenAnswer(inv -> {
            UserRequest r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });
    }

    private UserRequest request(Long id, RequestType type, ClassSection classSection, User requester,
                                RequestStatus status) {
        UserRequest r = new UserRequest();
        r.setId(id);
        r.setRequestType(type);
        r.setRequester(requester);
        r.setStudent(student);
        r.setClassSection(classSection);
        r.setTitle("Đơn xin nghỉ học");
        r.setReason("Ốm");
        r.setFromDate(AppTime.today().minusDays(3));
        r.setToDate(AppTime.today().minusDays(1));
        r.setStatus(status);
        return r;
    }

    private static Attendance absence(Long id, ClassSection classSection, LocalDate date) {
        ClassSession session = new ClassSession();
        session.setClassSection(classSection);
        session.setSessionNo((short) 3);
        session.setSessionDate(date);
        Attendance row = new Attendance();
        row.setId(id);
        row.setSession(session);
        row.setStatus(AttendanceStatus.ABSENT_UNEXCUSED);
        row.setNote("");
        return row;
    }

    private List<AuditLog> audits() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("Tạo đơn")
    class Create {

        private LeaveRequestForm form(Long sectionId) {
            LeaveRequestForm form = new LeaveRequestForm();
            form.setSectionId(sectionId);
            form.setFromDate(AppTime.today());
            form.setToDate(AppTime.today().plusDays(1));
            form.setReason("Ốm");
            return form;
        }

        @Test
        @DisplayName("SV gửi đơn có lớp: PENDING, người gửi là SV, tiêu đề tự điền, báo giảng viên lớp, có audit")
        void studentCreatesLeave() {
            when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L))
                    .thenReturn(Optional.of(CatalogTestData.enrollment(1L, section, student, EnrollmentStatus.ENROLLED)));

            Long id = service.createLeave(STUDENT, View.STUDENT, null, form(10L), List.of(), "127.0.0.1");

            ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
            verify(requestRepository).save(captor.capture());
            UserRequest saved = captor.getValue();
            assertThat(id).isEqualTo(50L);
            assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
            assertThat(saved.getRequester()).isSameAs(studentUser);
            assertThat(saved.getStudent()).isSameAs(student);
            assertThat(saved.getClassSection()).isSameAs(section);
            assertThat(saved.getTitle()).isEqualTo(RequestRule.leaveTitle(AppTime.today(), AppTime.today().plusDays(1)));
            verify(notificationService).sendAutomatic(eq(4L), argThat(t -> t.startsWith("Đơn mới cần duyệt")),
                    any(), eq(NotificationPriority.NORMAL), eq(NotificationTargetType.USER), eq(3L),
                    argThat(r -> r.size() == 1 && r.contains(3L)));
            assertThat(audits()).extracting(AuditLog::getAction).containsExactly(AuditActions.CREATE_REQUEST);
        }

        @Test
        @DisplayName("Không chọn lớp: báo GVCN")
        void notifiesHomeroomWithoutSection() {
            service.createLeave(STUDENT, View.STUDENT, null, form(null), List.of(), "127.0.0.1");

            verify(notificationService).sendAutomatic(eq(4L), any(), any(), any(), eq(NotificationTargetType.USER),
                    eq(16L), argThat(r -> r.contains(16L)));
        }

        @Test
        @DisplayName("Lớp SV không đang học (đã rút): lỗi ở ô lớp học phần, không lưu")
        void sectionMustBeEnrolled() {
            when(enrollmentRepository.findByClassSectionIdAndStudentId(10L, 1L))
                    .thenReturn(Optional.of(CatalogTestData.enrollment(1L, section, student, EnrollmentStatus.DROPPED)));

            assertThatThrownBy(() -> service.createLeave(STUDENT, View.STUDENT, null, form(10L), List.of(), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("sectionId", RequestMessages.SECTION_NOT_ENROLLED));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ngày sai ngưỡng (15 ngày trước): lỗi theo ô, không lưu")
        void invalidDates() {
            LeaveRequestForm form = form(null);
            form.setFromDate(AppTime.today().minusDays(15));

            assertThatThrownBy(() -> service.createLeave(STUDENT, View.STUDENT, null, form, List.of(), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("fromDate", RequestMessages.FROM_TOO_OLD));
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("PH gửi thay con: người gửi là PH, SV là con; con chưa liên kết → 403")
        void parentCreatesForChild() {
            service.createLeave(PARENT, View.PARENT, 1L, form(null), List.of(), null);

            ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
            verify(requestRepository).save(captor.capture());
            assertThat(captor.getValue().getRequester()).isSameAs(parentUser);
            assertThat(captor.getValue().getStudent()).isSameAs(student);

            assertThatThrownBy(() -> service.createLeave(PARENT, View.PARENT, 2L, form(null), List.of(), null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Đơn khác không nhận loại LEAVE")
        void otherRejectsLeaveType() {
            OtherRequestForm form = new OtherRequestForm();
            form.setRequestType(RequestType.LEAVE);
            form.setTitle("Xin bảng điểm");
            form.setReason("Nộp hồ sơ học bổng");

            assertThatThrownBy(() -> service.createOther(STUDENT, form, List.of(), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("requestType", RequestMessages.TYPE_INVALID));
        }
    }

    @Nested
    @DisplayName("Hủy đơn")
    class Cancel {

        @Test
        @DisplayName("Người gửi hủy đơn PENDING: chuyển CANCELLED bằng UPDATE có điều kiện, có audit")
        void requesterCancelsPending() {
            when(requestRepository.findById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.PENDING)));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.CANCELLED), any())).thenReturn(1);

            service.cancel(STUDENT, 7L, "127.0.0.1");

            verify(requestRepository).transitionFromPending(eq(7L), eq(RequestStatus.CANCELLED), any());
            assertThat(audits()).extracting(AuditLog::getAction).containsExactly(AuditActions.CANCEL_REQUEST);
        }

        @Test
        @DisplayName("Đơn không còn PENDING (đã duyệt): lỗi nghiệp vụ, không audit")
        void cannotCancelProcessed() {
            when(requestRepository.findById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.APPROVED)));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.CANCELLED), any())).thenReturn(0);

            assertThatThrownBy(() -> service.cancel(STUDENT, 7L, null))
                    .isInstanceOf(BusinessException.class).hasMessage(RequestMessages.NOT_PENDING);
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Không phải người gửi (SV khi PH gửi thay, người ngoài, id không tồn tại) → 403")
        void onlyRequester() {
            when(requestRepository.findById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, parentUser, RequestStatus.PENDING)));

            assertThatThrownBy(() -> service.cancel(STUDENT, 7L, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.cancel(OUTSIDER, 7L, null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.cancel(STUDENT, 99L, null)).isInstanceOf(AccessDeniedException.class);
            verify(requestRepository, never()).transitionFromPending(anyLong(), any(), any());
        }
    }

    @Nested
    @DisplayName("Duyệt / từ chối")
    class Review {

        @Test
        @DisplayName("Duyệt đơn nghỉ có lớp: chỉ đổi các dòng vắng không phép của SV trong khoảng ngày, đúng lớp; "
                + "ghi chú tham chiếu đơn; audit từng dòng; người gửi và SV nhận thông báo")
        void approveExcusesAbsences() {
            UserRequest r = request(7L, RequestType.LEAVE, section, parentUser, RequestStatus.PENDING);
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(r));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.APPROVED), any())).thenReturn(1);
            Attendance a1 = absence(100L, section, AppTime.today().minusDays(3));
            Attendance a2 = absence(101L, section, AppTime.today().minusDays(1));
            a2.setNote("Không báo trước");
            when(attendanceRepository.findUnexcusedInRange(1L, r.getFromDate(), r.getToDate(), 10L))
                    .thenReturn(List.of(a1, a2));

            ReviewResult result = service.review(TEACHER, View.TEACHER, 7L, ReviewDecision.APPROVED, null, "1.2.3.4");

            assertThat(result.excusedSessions()).isEqualTo(2);
            assertThat(List.of(a1, a2)).allSatisfy(a -> {
                assertThat(a.getStatus()).isEqualTo(AttendanceStatus.ABSENT_EXCUSED);
                assertThat(a.getNote()).isEqualTo("Vắng có phép theo đơn #7");
            });
            verify(attendanceRepository).saveAllAndFlush(List.of(a1, a2));
            ArgumentCaptor<RequestReview> review = ArgumentCaptor.forClass(RequestReview.class);
            verify(reviewRepository).save(review.capture());
            assertThat(review.getValue().getDecision()).isEqualTo(ReviewDecision.APPROVED);
            List<AuditLog> audits = audits();
            assertThat(audits).extracting(AuditLog::getAction).containsExactly(AuditActions.EXCUSE_ATTENDANCE,
                    AuditActions.EXCUSE_ATTENDANCE, AuditActions.APPROVE_REQUEST);
            assertThat(audits.get(1).getDetail()).contains("ABSENT_UNEXCUSED -> ABSENT_EXCUSED",
                    "oldNote=Không báo trước", "request=7");
            verify(notificationService).sendAutomatic(eq(3L), argThat(t -> t.startsWith("Đơn đã được duyệt")),
                    argThat(c -> c.contains("2 buổi")), any(), eq(NotificationTargetType.USER), eq(4L),
                    argThat(u -> u.containsAll(List.of(4L, 5L)) && u.size() == 2));
        }

        @Test
        @DisplayName("Đơn nghỉ không chọn lớp: truy vấn mọi lớp (sectionId null), GVCN duyệt")
        void approveWithoutSectionQueriesAllSections() {
            UserRequest r = request(7L, RequestType.LEAVE, null, studentUser, RequestStatus.PENDING);
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(r));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.APPROVED), any())).thenReturn(1);

            service.review(HOMEROOM, View.TEACHER, 7L, ReviewDecision.APPROVED, "Đồng ý", null);

            verify(attendanceRepository).findUnexcusedInRange(eq(1L), eq(r.getFromDate()), eq(r.getToDate()),
                    isNull());
        }

        @Test
        @DisplayName("Từ chối thiếu ghi chú: lỗi ở ô ghi chú, không đổi trạng thái")
        void rejectRequiresComment() {
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.PENDING)));

            assertThatThrownBy(() -> service.review(TEACHER, View.TEACHER, 7L, ReviewDecision.REJECTED, "  ", null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getField()).isEqualTo("comment"));
            verify(requestRepository, never()).transitionFromPending(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Từ chối kèm ghi chú: REJECTED, không đụng điểm danh, thông báo mức Quan trọng")
        void rejectDoesNotTouchAttendance() {
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.PENDING)));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.REJECTED), any())).thenReturn(1);

            ReviewResult result = service.review(TEACHER, View.TEACHER, 7L, ReviewDecision.REJECTED,
                    "Thiếu minh chứng", null);

            assertThat(result.excusedSessions()).isZero();
            verify(attendanceRepository, never()).findUnexcusedInRange(anyLong(), any(), any(), any());
            assertThat(audits()).extracting(AuditLog::getAction).containsExactly(AuditActions.REJECT_REQUEST);
            verify(notificationService).sendAutomatic(eq(3L), argThat(t -> t.startsWith("Đơn bị từ chối")),
                    argThat(c -> c.contains("Thiếu minh chứng")), eq(NotificationPriority.IMPORTANT), any(), any(),
                    any());
        }

        @Test
        @DisplayName("Duyệt đơn đã hủy (UPDATE có điều kiện trả 0): lỗi nghiệp vụ, không ghi review, không đổi điểm danh")
        void approveCancelledRejected() {
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.CANCELLED)));
            when(requestRepository.transitionFromPending(eq(7L), eq(RequestStatus.APPROVED), any())).thenReturn(0);

            assertThatThrownBy(() -> service.review(TEACHER, View.TEACHER, 7L, ReviewDecision.APPROVED, null, null))
                    .isInstanceOf(BusinessException.class).hasMessage(RequestMessages.NOT_PENDING);
            verify(reviewRepository, never()).save(any());
            verify(attendanceRepository, never()).findUnexcusedInRange(anyLong(), any(), any(), any());
            verify(notificationService, never()).sendAutomatic(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Không phải người duyệt → 403: GV khác, GVCN khi đơn có lớp, ADMIN với đơn của GV, GV với đơn khác")
        void onlyReviewer() {
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.PENDING)));
            when(requestRepository.findDetailById(8L)).thenReturn(Optional.of(
                    request(8L, RequestType.TRANSCRIPT, null, studentUser, RequestStatus.PENDING)));

            assertThatThrownBy(() -> service.review(HOMEROOM, View.TEACHER, 7L, ReviewDecision.APPROVED, null, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.review(ADMIN, View.ADMIN, 7L, ReviewDecision.APPROVED, null, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.review(HOMEROOM, View.TEACHER, 8L, ReviewDecision.APPROVED, null, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.detail(HOMEROOM, View.TEACHER, null, 7L))
                    .isInstanceOf(AccessDeniedException.class);
            verify(requestRepository, never()).transitionFromPending(anyLong(), any(), any());
        }

        @Test
        @DisplayName("ADMIN duyệt đơn khác: không đổi điểm danh")
        void adminApprovesOther() {
            when(requestRepository.findDetailById(8L)).thenReturn(Optional.of(
                    request(8L, RequestType.TRANSCRIPT, null, studentUser, RequestStatus.PENDING)));
            when(requestRepository.transitionFromPending(eq(8L), eq(RequestStatus.APPROVED), any())).thenReturn(1);

            ReviewResult result = service.review(ADMIN, View.ADMIN, 8L, ReviewDecision.APPROVED, null, null);

            assertThat(result.decision()).isEqualTo(ReviewDecision.APPROVED);
            verify(attendanceRepository, never()).findUnexcusedInRange(anyLong(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Tải tệp")
    class Download {

        @BeforeEach
        void file() {
            StoredFile stored = new StoredFile();
            stored.setId(70L);
            stored.setFileName("giay-kham.pdf");
            stored.setFileUrl("attachments/x.pdf");
            stored.setContentType("application/pdf");
            RequestFile link = new RequestFile();
            link.setFile(stored);
            when(requestFileRepository.findByIdRequestIdAndIdFileId(7L, 70L)).thenReturn(Optional.of(link));
            when(requestRepository.findDetailById(7L)).thenReturn(Optional.of(
                    request(7L, RequestType.LEAVE, section, studentUser, RequestStatus.PENDING)));
            when(attachmentStorage.resolve("attachments/x.pdf")).thenReturn(Optional.of(Path.of("x.pdf")));
            Parent parent = new Parent();
            parent.setId(1L);
            when(parentRepository.findByUserId(5L)).thenReturn(Optional.of(parent));
            when(parentStudentRepository.existsByIdParentIdAndIdStudentId(1L, 1L)).thenReturn(true);
        }

        @Test
        @DisplayName("Người gửi, PH liên kết, người duyệt, ADMIN tải được")
        void allowed() {
            for (AppUserPrincipal actor : List.of(STUDENT, PARENT, TEACHER, ADMIN)) {
                assertThat(service.download(actor, 7L, 70L).fileName()).isEqualTo("giay-kham.pdf");
            }
        }

        @Test
        @DisplayName("Người không liên quan (SV khác, GV không phải người duyệt), tệp không thuộc đơn → 403")
        void denied() {
            assertThatThrownBy(() -> service.download(OUTSIDER, 7L, 70L)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.download(HOMEROOM, 7L, 70L)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.download(STUDENT, 7L, 71L)).isInstanceOf(AccessDeniedException.class);
            verify(attachmentStorage, times(0)).resolve(any());
        }
    }
}
