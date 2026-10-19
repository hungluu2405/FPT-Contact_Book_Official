package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ActivityForm;
import vn.edu.fpt.contactbook.dto.ActivityView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.club;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.member;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.user;

/**
 * Hoạt động CLB: tạo / sửa (thời gian, số lượng, CLB INACTIVE), đăng ký (thành viên ACTIVE, đầy chỗ, đã bắt đầu,
 * đã hủy, đăng ký lại dùng dòng cũ, khóa dòng hoạt động trước khi đếm), hủy đăng ký, ghi nhận tham dự (trước giờ
 * bắt đầu bị từ chối), cờ hiển thị theo trạng thái tính theo thời gian.
 * CLB 1: chủ nhiệm users.id 18, thành viên users.id 19, hoạt động 50 bắt đầu sau 2 ngày.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubActivityServiceTest {

    static final AppUserPrincipal PRESIDENT = CatalogTestData.principal(18L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal MEMBER = CatalogTestData.principal(19L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal OUTSIDER = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());

    @Mock ClubRepository clubRepository;
    @Mock ClubActivityRepository activityRepository;
    @Mock ClubActivityParticipantRepository participantRepository;
    @Mock ClubMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogRepository auditLogRepository;

    private ClubActivityService service;
    private Club club;
    private ClubActivity activity;

    @BeforeEach
    void setUp() {
        service = new ClubActivityService(clubRepository, activityRepository, participantRepository, userRepository,
                new ClubAccessPolicy(memberRepository), new AuditService(auditLogRepository, userRepository));
        club = club(1L, ClubStatus.ACTIVE);
        activity = activity(50L, AppTime.now().plusDays(2), (short) 2);
        when(clubRepository.findById(1L)).thenReturn(Optional.of(club));
        when(activityRepository.findById(50L)).thenReturn(Optional.of(activity));
        when(activityRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(activity));
        when(userRepository.getReferenceById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0)));
        when(memberRepository.findByClubIdAndUserId(1L, 18L))
                .thenReturn(Optional.of(member(1L, club, 18L, ClubMemberRole.PRESIDENT, ClubMemberStatus.ACTIVE)));
        when(memberRepository.findByClubIdAndUserId(1L, 19L))
                .thenReturn(Optional.of(member(2L, club, 19L, ClubMemberRole.MEMBER, ClubMemberStatus.ACTIVE)));
    }

    @Nested
    @DisplayName("Tạo và sửa hoạt động")
    class Manage {

        @Test
        @DisplayName("Chủ nhiệm tạo: lưu PLANNED, người tạo, có audit")
        void create() {
            service.create(PRESIDENT, 1L, form(AppTime.now().plusDays(1), 3, 30), null);

            ArgumentCaptor<ClubActivity> saved = ArgumentCaptor.forClass(ClubActivity.class);
            verify(activityRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(ClubActivityStatus.PLANNED);
            assertThat(saved.getValue().getCreatedBy().getId()).isEqualTo(18L);
            assertThat(saved.getValue().getMaxParticipants()).isEqualTo((short) 30);
            assertThat(audit().getAction()).isEqualTo(AuditActions.CREATE_CLUB_ACTIVITY);
        }

        @Test
        @DisplayName("Thời gian và số lượng sai: lỗi theo ô, không lưu")
        void invalid() {
            LocalDateTime start = AppTime.now().plusDays(1);
            ActivityForm endBeforeStart = form(start, 0, null);
            endBeforeStart.setEndTime(start);
            assertThatThrownBy(() -> service.create(PRESIDENT, 1L, endBeforeStart, null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("endTime", ClubMessages.END_AFTER_START));
            assertThatThrownBy(() -> service.create(PRESIDENT, 1L, form(AppTime.now().minusHours(1), 3, null), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("startTime", ClubMessages.START_IN_PAST));
            assertThatThrownBy(() -> service.create(PRESIDENT, 1L, form(start, 3, 0), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("maxParticipants", ClubMessages.MAX_RANGE));
            verify(activityRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("CLB INACTIVE không tạo được; thành viên thường và giảng viên: 403")
        void guards() {
            assertThatThrownBy(() -> service.create(MEMBER, 1L, form(AppTime.now().plusDays(1), 3, null), null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.create(TEACHER, 1L, form(AppTime.now().plusDays(1), 3, null), null))
                    .isInstanceOf(AccessDeniedException.class);
            club.setStatus(ClubStatus.INACTIVE);
            assertThatThrownBy(() -> service.create(PRESIDENT, 1L, form(AppTime.now().plusDays(1), 3, null), null))
                    .hasMessage(ClubMessages.CLUB_INACTIVE);
            verify(activityRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Sửa: số tối đa không nhỏ hơn số đã đăng ký; hoạt động đã bắt đầu không sửa được")
        void update() {
            when(participantRepository.countTaken(50L)).thenReturn(2L);
            assertThatThrownBy(() -> service.update(PRESIDENT, 1L, 50L, form(AppTime.now().plusDays(3), 2, 1), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("maxParticipants", ClubMessages.MAX_BELOW_TAKEN.formatted(2L)));

            activity.setStartTime(AppTime.now().minusMinutes(1));
            assertThatThrownBy(() -> service.update(PRESIDENT, 1L, 50L, form(AppTime.now().plusDays(3), 2, 5), null))
                    .hasMessage(ClubMessages.ACTIVITY_STARTED);
            assertThatThrownBy(() -> service.cancel(PRESIDENT, 1L, 50L, null))
                    .hasMessage(ClubMessages.ACTIVITY_STARTED);
        }

        @Test
        @DisplayName("Hoạt động thuộc CLB khác: 404")
        void otherClub() {
            activity.setClub(club(2L, ClubStatus.ACTIVE));
            assertThatThrownBy(() -> service.cancel(PRESIDENT, 1L, 50L, null))
                    .hasMessage(ClubMessages.ACTIVITY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Đăng ký hoạt động")
    class Register {

        @Test
        @DisplayName("Thành viên ACTIVE đăng ký khi còn chỗ: khóa dòng hoạt động TRƯỚC khi đếm, lưu REGISTERED")
        void registers() {
            when(participantRepository.countTaken(50L)).thenReturn(1L);

            service.register(MEMBER, 1L, 50L);

            InOrder order = inOrder(activityRepository, participantRepository);
            order.verify(activityRepository).findByIdForUpdate(50L);
            order.verify(participantRepository).countTaken(50L);
            ArgumentCaptor<ClubActivityParticipant> saved = ArgumentCaptor.forClass(ClubActivityParticipant.class);
            verify(participantRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getId()).isEqualTo(new ClubActivityParticipantId(50L, 19L));
            assertThat(saved.getValue().getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
        }

        @Test
        @DisplayName("Đầy chỗ (REGISTERED + ATTENDED = tối đa): từ chối, không lưu")
        void full() {
            when(participantRepository.countTaken(50L)).thenReturn(2L);

            assertThatThrownBy(() -> service.register(MEMBER, 1L, 50L)).hasMessage(ClubMessages.ACTIVITY_FULL);
            verify(participantRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Đăng ký lại sau khi hủy: cập nhật chính dòng cũ về REGISTERED và registered_at mới")
        void reRegister() {
            ClubActivityParticipant old = participant(19L, ParticipantStatus.CANCELLED);
            old.setRegisteredAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            when(participantRepository.findById(new ClubActivityParticipantId(50L, 19L))).thenReturn(Optional.of(old));
            when(participantRepository.countTaken(50L)).thenReturn(1L);

            service.register(MEMBER, 1L, 50L);

            verify(participantRepository).saveAndFlush(old);
            assertThat(old.getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
            assertThat(old.getRegisteredAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("Đã đăng ký rồi: báo lỗi; hoạt động đã bắt đầu hoặc đã hủy: từ chối")
        void guards() {
            when(participantRepository.findById(new ClubActivityParticipantId(50L, 19L)))
                    .thenReturn(Optional.of(participant(19L, ParticipantStatus.REGISTERED)));
            assertThatThrownBy(() -> service.register(MEMBER, 1L, 50L)).hasMessage(ClubMessages.ALREADY_REGISTERED);

            when(participantRepository.findById(new ClubActivityParticipantId(50L, 19L))).thenReturn(Optional.empty());
            activity.setStartTime(AppTime.now().minusMinutes(5));
            assertThatThrownBy(() -> service.register(MEMBER, 1L, 50L)).hasMessage(ClubMessages.ACTIVITY_STARTED);

            activity.setStartTime(AppTime.now().plusDays(1));
            activity.setStatus(ClubActivityStatus.CANCELLED);
            assertThatThrownBy(() -> service.register(MEMBER, 1L, 50L)).hasMessage(ClubMessages.ACTIVITY_CANCELLED);
            verify(participantRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Không phải thành viên ACTIVE (sinh viên ngoài CLB, giảng viên): 403")
        void notMember() {
            assertThatThrownBy(() -> service.register(OUTSIDER, 1L, 50L)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.register(TEACHER, 1L, 50L)).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Hủy đăng ký trước giờ bắt đầu: CANCELLED; sau giờ bắt đầu: từ chối")
        void unregister() {
            ClubActivityParticipant mine = participant(19L, ParticipantStatus.REGISTERED);
            when(participantRepository.findById(new ClubActivityParticipantId(50L, 19L))).thenReturn(Optional.of(mine));

            service.unregister(MEMBER, 1L, 50L);
            assertThat(mine.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);

            mine.setStatus(ParticipantStatus.REGISTERED);
            activity.setStartTime(AppTime.now().minusMinutes(1));
            assertThatThrownBy(() -> service.unregister(MEMBER, 1L, 50L)).hasMessage(ClubMessages.ACTIVITY_STARTED);
            assertThat(mine.getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
        }
    }

    @Nested
    @DisplayName("Ghi nhận tham dự và cờ hiển thị")
    class Attendance {

        @Test
        @DisplayName("Trước giờ bắt đầu: từ chối, không đổi ai")
        void beforeStart() {
            ClubActivityParticipant p = participant(19L, ParticipantStatus.REGISTERED);
            when(participantRepository.findTaken(50L)).thenReturn(List.of(p));

            assertThatThrownBy(() -> service.markAttendance(PRESIDENT, 1L, 50L, List.of(19L), null))
                    .hasMessage(ClubMessages.ACTIVITY_NOT_STARTED);
            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
        }

        @Test
        @DisplayName("Sau giờ bắt đầu: người được chọn ATTENDED, còn lại REGISTERED; chọn người lạ thì lỗi")
        void afterStart() {
            activity.setStartTime(AppTime.now().minusHours(1));
            ClubActivityParticipant a = participant(19L, ParticipantStatus.REGISTERED);
            ClubActivityParticipant b = participant(20L, ParticipantStatus.ATTENDED);
            when(participantRepository.findTaken(50L)).thenReturn(List.of(a, b));

            assertThat(service.markAttendance(PRESIDENT, 1L, 50L, List.of(19L), null)).isEqualTo(1);
            assertThat(a.getStatus()).isEqualTo(ParticipantStatus.ATTENDED);
            assertThat(b.getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
            assertThat(audit().getAction()).isEqualTo(AuditActions.MARK_CLUB_ATTENDANCE);

            assertThatThrownBy(() -> service.markAttendance(PRESIDENT, 1L, 50L, List.of(99L), null))
                    .hasMessage(ClubMessages.PARTICIPANT_UNKNOWN);
            assertThatThrownBy(() -> service.markAttendance(MEMBER, 1L, 50L, List.of(19L), null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Cờ: thành viên thấy nút đăng ký khi còn chỗ; đang diễn ra thì không đăng ký, chủ nhiệm ghi nhận được")
        void flags() {
            when(activityRepository.findUpcoming(any(), any())).thenReturn(List.of(activity));
            when(participantRepository.countTakenByActivities(List.of(50L))).thenReturn(List.<Object[]>of(
                    new Object[]{50L, 1L}));

            ActivityView planned = service.upcoming(MEMBER, 1L).getFirst();
            assertThat(planned.state()).isEqualTo(ClubActivityStatus.PLANNED);
            assertThat(planned.canRegister()).isTrue();
            assertThat(planned.canEdit()).isFalse();
            assertThat(service.upcoming(PRESIDENT, 1L).getFirst().canEdit()).isTrue();
            assertThat(service.upcoming(OUTSIDER, 1L).getFirst().canRegister()).isFalse();

            activity.setStartTime(AppTime.now().minusMinutes(10));
            ActivityView ongoing = service.upcoming(PRESIDENT, 1L).getFirst();
            assertThat(ongoing.state()).isEqualTo(ClubActivityStatus.ONGOING);
            assertThat(ongoing.canRegister()).isFalse();
            assertThat(ongoing.canEdit()).isFalse();
            assertThat(ongoing.canMarkAttendance()).isTrue();
        }
    }

    private AuditLog audit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private ClubActivity activity(Long id, LocalDateTime start, Short max) {
        ClubActivity a = new ClubActivity();
        a.setId(id);
        a.setClub(club);
        a.setTitle("Hoạt động " + id);
        a.setStartTime(start);
        a.setEndTime(start.plusHours(3));
        a.setMaxParticipants(max);
        a.setStatus(ClubActivityStatus.PLANNED);
        return a;
    }

    private ClubActivityParticipant participant(Long userId, ParticipantStatus status) {
        ClubActivityParticipant p = new ClubActivityParticipant();
        p.setId(new ClubActivityParticipantId(50L, userId));
        p.setActivity(activity);
        p.setUser(user(userId));
        p.setStatus(status);
        return p;
    }

    static ActivityForm form(LocalDateTime start, int hours, Integer max) {
        ActivityForm form = new ActivityForm();
        form.setTitle("Workshop Spring Boot");
        form.setLocation("Phòng 201");
        form.setStartTime(start);
        form.setEndTime(start.plusHours(hours));
        form.setMaxParticipants(max);
        return form;
    }
}
