package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.dto.NotificationDetail;
import vn.edu.fpt.contactbook.dto.NotificationForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Notification;
import vn.edu.fpt.contactbook.model.NotificationFile;
import vn.edu.fpt.contactbook.model.NotificationRecipient;
import vn.edu.fpt.contactbook.model.NotificationRecipientId;
import vn.edu.fpt.contactbook.model.StoredFile;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.NotificationAudienceRepository;
import vn.edu.fpt.contactbook.repository.NotificationFileRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.repository.NotificationRepository;
import vn.edu.fpt.contactbook.repository.RoleRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.StoredFileRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gửi thông báo: kiểm tra form, phạm vi kiểm tra lại ở service, người nhận loại người gửi, tệp đính kèm, audit;
 * xem chi tiết (đánh dấu đã đọc, 403 với người ngoài), tải tệp.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal OTHER = CatalogTestData.principal(9L, Set.of(RoleCodes.STUDENT), Map.of());

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationRecipientRepository recipientRepository;
    @Mock NotificationFileRepository notificationFileRepository;
    @Mock StoredFileRepository storedFileRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock NotificationAudienceRepository audienceRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock AuditLogRepository auditLogRepository;

    @TempDir
    Path uploadDir;

    private NotificationService service;
    private AttachmentStorage storage;

    @BeforeEach
    void setUp() {
        UserAccessPolicy policy = new UserAccessPolicy(teacherRepository);
        storage = new AttachmentStorage(uploadDir.toString());
        service = new NotificationService(notificationRepository, recipientRepository, notificationFileRepository,
                storedFileRepository, userRepository, roleRepository, departmentRepository, schoolClassRepository,
                classSectionRepository, new NotificationAudience(audienceRepository, classSectionRepository,
                teacherRepository, mock(SemesterRepository.class), policy), storage, mock(CatalogLookupService.class),
                policy,
                new AuditService(auditLogRepository, userRepository));

        Department se = CatalogTestData.department(1L, "SE");
        Teacher gv002 = CatalogTestData.teacher(2L, "GV002", se);
        when(teacherRepository.findByUserId(3L)).thenReturn(Optional.of(gv002));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(CatalogTestData.section(10L, "TOAN10-01",
                CatalogTestData.subject(1L, "TOAN10", se), CatalogTestData.semester(1L, "HK1-2627"), gv002, 40,
                SectionStatus.OPEN)));
        when(audienceRepository.studentsOfSection(10L)).thenReturn(List.of(4L, 3L));
        when(audienceRepository.parentsOfSection(10L)).thenReturn(List.of(5L));
        when(userRepository.getReferenceById(any())).thenAnswer(invocation -> user(invocation.getArgument(0)));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(77L);
            return n;
        });
    }

    @Test
    @DisplayName("GV gửi lớp mình: người nhận là SV + PH, loại chính người gửi; lưu tệp; audit phạm vi và số người")
    void teacherSendsToOwnSection() {
        Long id = service.send(TEACHER, form(NotificationTargetType.CLASS_SECTION, 10L),
                List.of(AttachmentStorageTest.pdf("de-cuong.pdf")), "127.0.0.1");

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<List<NotificationRecipient>> rows = ArgumentCaptor.forClass(List.class);
        verify(recipientRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(r -> r.getId().getUserId()).containsExactly(4L, 5L);
        verify(storedFileRepository).save(any(StoredFile.class));
        verify(notificationFileRepository).save(any(NotificationFile.class));
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditActions.SEND_NOTIFICATION);
        assertThat(audit.getValue().getDetail()).isEqualTo("target=CLASS_SECTION:10, recipients=2");
    }

    @Test
    @DisplayName("GV sửa sectionId sang lớp khác → 403, không lưu gì")
    void tamperedTargetIsForbidden() {
        assertThatThrownBy(() -> service.send(TEACHER, form(NotificationTargetType.CLASS_SECTION, 99L), List.of(),
                "127.0.0.1")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.send(TEACHER, form(NotificationTargetType.ALL, null), List.of(),
                "127.0.0.1")).isInstanceOf(AccessDeniedException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tệp sai loại → lỗi, không lưu thông báo")
    void invalidAttachmentRejected() {
        assertThatThrownBy(() -> service.send(TEACHER, form(NotificationTargetType.CLASS_SECTION, 10L),
                List.of(new AttachmentUpload("a.exe", "application/octet-stream", new byte[]{1})), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không còn người nhận ngoài chính người gửi → lỗi")
    void noRecipients() {
        when(audienceRepository.studentsOfSection(10L)).thenReturn(List.of(3L));
        when(audienceRepository.parentsOfSection(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.send(TEACHER, form(NotificationTargetType.CLASS_SECTION, 10L), List.of(),
                "127.0.0.1")).isInstanceOf(BusinessException.class).hasMessage(NotificationMessages.NO_RECIPIENTS);
    }

    @Test
    @DisplayName("Thiếu tiêu đề, nội dung, nơi nhận → lỗi theo ô; tiêu đề quá 200 ký tự bị từ chối")
    void fieldErrors() {
        NotificationForm empty = new NotificationForm();
        empty.setTargetType(NotificationTargetType.CLASS_SECTION);
        assertThatThrownBy(() -> service.send(TEACHER, empty, List.of(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getFieldErrors())
                        .containsKeys("title", "content", "sectionId"));

        NotificationForm longTitle = form(NotificationTargetType.CLASS_SECTION, 10L);
        longTitle.setTitle("x".repeat(201));
        assertThatThrownBy(() -> service.send(TEACHER, longTitle, List.of(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(NotificationMessages.TITLE_SIZE);
    }

    @Test
    @DisplayName("Gửi cá nhân bằng số điện thoại: chuẩn hóa số; số không có hoặc tài khoản khóa → lỗi ở ô")
    void sendToUserByPhone() {
        User student = user(4L);
        student.setPhone("0900000004");
        when(userRepository.findByPhone("0900000004")).thenReturn(Optional.of(student));
        when(audienceRepository.isStudentOfTeacher(4L, 2L)).thenReturn(true);
        when(audienceRepository.activeAmong(List.of(4L))).thenReturn(List.of(4L));
        NotificationForm form = form(NotificationTargetType.USER, null);
        form.setRecipientPhone("+84 900 000 004");

        assertThat(service.send(TEACHER, form, List.of(), "127.0.0.1")).isEqualTo(77L);

        User locked = user(8L);
        locked.setActive(false);
        when(userRepository.findByPhone("0900000008")).thenReturn(Optional.of(locked));
        form.setRecipientPhone("0900000008");
        assertThatThrownBy(() -> service.send(TEACHER, form, List.of(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessage(NotificationMessages.PHONE_NOT_FOUND);
    }

    @Test
    @DisplayName("Gửi tự động: loại người gửi, không còn ai thì không lưu")
    void automatic() {
        assertThat(service.sendAutomatic(3L, "Hủy buổi", "Nội dung", NotificationPriority.IMPORTANT,
                NotificationTargetType.CLASS_SECTION, 10L, List.of(3L))).isEmpty();
        verify(notificationRepository, never()).save(any());

        assertThat(service.sendAutomatic(3L, "Hủy buổi", "Nội dung", NotificationPriority.IMPORTANT,
                NotificationTargetType.CLASS_SECTION, 10L, List.of(3L, 4L, 5L))).contains(77L);
        verify(recipientRepository).saveAll(anyIterable());
    }

    @Test
    @DisplayName("Người nhận mở chi tiết lần đầu thì đánh dấu đã đọc; người ngoài nhận 403")
    void openMarksReadAndChecksAccess() {
        Notification n = notification();
        NotificationRecipient row = new NotificationRecipient();
        row.setId(new NotificationRecipientId(77L, 4L));
        when(notificationRepository.findById(77L)).thenReturn(Optional.of(n));
        when(recipientRepository.findById(new NotificationRecipientId(77L, 4L))).thenReturn(Optional.of(row));
        when(recipientRepository.findById(new NotificationRecipientId(77L, 9L))).thenReturn(Optional.empty());

        NotificationDetail detail = service.open(STUDENT, 77L, null, 0);

        assertThat(detail.sender()).isFalse();
        assertThat(detail.recipients()).isNull();
        assertThat(row.isRead()).isTrue();
        assertThat(row.getReadAt()).isNotNull();
        assertThatThrownBy(() -> service.open(OTHER, 77L, null, 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.open(OTHER, 999L, null, 0)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Người gửi xem thống kê đã đọc")
    void senderSeesStats() {
        when(notificationRepository.findById(77L)).thenReturn(Optional.of(notification()));
        when(recipientRepository.countReadStats(List.of(77L))).thenReturn(List.<Object[]>of(new Object[]{77L, 4L, 1L}));
        when(recipientRepository.findRecipients(any(), any(), any())).thenReturn(Page.empty());

        NotificationDetail detail = service.open(TEACHER, 77L, null, 0);

        assertThat(detail.sender()).isTrue();
        assertThat(detail.recipientCount()).isEqualTo(4);
        assertThat(detail.readCount()).isEqualTo(1);
        assertThat(detail.readPercent()).isEqualTo(25);
    }

    @Test
    @DisplayName("Tải tệp: người nhận, người gửi, ADMIN được; người khác 403")
    void downloadAccess() {
        AttachmentStorage.Stored stored = storage.store(AttachmentStorageTest.pdf("a.pdf"));
        StoredFile file = new StoredFile();
        file.setId(5L);
        file.setFileName("a.pdf");
        file.setFileUrl(stored.key());
        file.setContentType("application/pdf");
        NotificationFile link = new NotificationFile();
        link.setNotification(notification());
        link.setFile(file);
        when(notificationFileRepository.findByIdNotificationIdAndIdFileId(77L, 5L)).thenReturn(Optional.of(link));
        when(recipientRepository.existsById(new NotificationRecipientId(77L, 4L))).thenReturn(true);

        assertThat(service.download(STUDENT, 77L, 5L).fileName()).isEqualTo("a.pdf");
        assertThat(service.download(TEACHER, 77L, 5L).path()).exists();
        assertThat(service.download(ADMIN, 77L, 5L)).isNotNull();
        assertThatThrownBy(() -> service.download(OTHER, 77L, 5L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.download(STUDENT, 78L, 5L)).isInstanceOf(AccessDeniedException.class);
    }

    private static Notification notification() {
        Notification n = new Notification();
        n.setId(77L);
        n.setSender(user(3L));
        n.setTitle("Tiêu đề");
        n.setContent("Nội dung");
        n.setTargetType(NotificationTargetType.CLASS_SECTION);
        n.setTargetId(10L);
        return n;
    }

    private static NotificationForm form(NotificationTargetType type, Long sectionId) {
        NotificationForm form = new NotificationForm();
        form.setTitle("Nghỉ học");
        form.setContent("Lớp nghỉ buổi thứ Hai.\nHọc bù sau.");
        form.setTargetType(type);
        form.setSectionId(sectionId);
        return form;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Người " + id);
        return user;
    }
}
