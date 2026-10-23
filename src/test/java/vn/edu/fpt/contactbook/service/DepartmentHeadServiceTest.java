package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.HeadNotificationForm;
import vn.edu.fpt.contactbook.dto.NotificationForm;
import vn.edu.fpt.contactbook.dto.UserDetailView;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentHeadServiceTest {

    static final AppUserPrincipal HEAD = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());

    @Mock UserAccessPolicy accessPolicy;
    @Mock CatalogLookupService catalogLookupService;
    @Mock UserService userService;
    @Mock NotificationService notificationService;
    @InjectMocks DepartmentHeadService service;

    @Test
    void notHeadIsForbiddenBeforeAnyLookup() {
        when(accessPolicy.headDepartmentIds(TEACHER)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.teachers(TEACHER, 1L, null, 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.send(TEACHER, new HeadNotificationForm(), List.of(), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userService, notificationService);
    }

    @Test
    void departmentSendTargetsTheDepartment() {
        when(accessPolicy.headDepartmentIds(HEAD)).thenReturn(Set.of(1L));
        when(notificationService.send(eq(HEAD), any(), eq(List.of()), eq("ip"))).thenReturn(9L);
        HeadNotificationForm form = form();
        form.setDepartmentId(1L);

        assertThat(service.send(HEAD, form, List.of(), "ip")).isEqualTo(9L);

        NotificationForm sent = captureSent();
        assertThat(sent.getTargetType()).isEqualTo(NotificationTargetType.DEPARTMENT);
        assertThat(sent.getDepartmentId()).isEqualTo(1L);
        assertThat(sent.getTitle()).isEqualTo("Họp tổ");
        assertThat(sent.getPriority()).isEqualTo(NotificationPriority.URGENT);
    }

    @Test
    void oneTeacherIsSentByTheTeacherPhoneAfterTheScopeCheck() {
        when(accessPolicy.headDepartmentIds(HEAD)).thenReturn(Set.of(1L));
        UserDetailView teacher = new UserDetailView(3L, "0900000003", "GV", null, null, null, null, true, false,
                null, null, List.of(), null, null, null, List.of(), List.of());
        when(userService.detail(HEAD, 3L)).thenReturn(teacher);
        HeadNotificationForm form = form();
        form.setDepartmentId(1L);
        form.setTeacherUserId(3L);

        service.send(HEAD, form, List.of(), "ip");

        NotificationForm sent = captureSent();
        assertThat(sent.getTargetType()).isEqualTo(NotificationTargetType.USER);
        assertThat(sent.getRecipientPhone()).isEqualTo("0900000003");
    }

    private HeadNotificationForm form() {
        HeadNotificationForm form = new HeadNotificationForm();
        form.setTitle("Họp tổ");
        form.setContent("Chiều thứ Năm");
        form.setPriority(NotificationPriority.URGENT);
        return form;
    }

    private NotificationForm captureSent() {
        ArgumentCaptor<NotificationForm> captor = ArgumentCaptor.forClass(NotificationForm.class);
        verify(notificationService).send(eq(HEAD), captor.capture(), eq(List.of()), any());
        return captor.getValue();
    }
}
