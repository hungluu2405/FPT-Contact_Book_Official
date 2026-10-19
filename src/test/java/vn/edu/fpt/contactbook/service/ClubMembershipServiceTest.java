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
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thành viên CLB: vòng đời yêu cầu (gửi, duyệt, từ chối, gửi lại dùng dòng cũ), quyền duyệt (chỉ chủ nhiệm của
 * đúng CLB hoặc ADMIN), đổi vai trò, rời CLB (chủ nhiệm không tự rời, hủy đăng ký hoạt động tương lai).
 * CLB 1: chủ nhiệm users.id 18; sinh viên users.id 4 gửi yêu cầu; users.id 20 là chủ nhiệm CLB 2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubMembershipServiceTest {

    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal PRESIDENT = CatalogTestData.principal(18L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal OTHER_PRESIDENT =
            CatalogTestData.principal(20L, Set.of(RoleCodes.STUDENT), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());

    @Mock ClubRepository clubRepository;
    @Mock ClubMemberRepository memberRepository;
    @Mock ClubActivityParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock AuditLogRepository auditLogRepository;

    private ClubMembershipService service;
    private Club club;
    private Club otherClub;

    @BeforeEach
    void setUp() {
        service = new ClubMembershipService(clubRepository, memberRepository, participantRepository, userRepository,
                new ClubAccessPolicy(memberRepository), notificationService,
                new AuditService(auditLogRepository, userRepository));
        club = club(1L, ClubStatus.ACTIVE);
        otherClub = club(2L, ClubStatus.ACTIVE);
        when(clubRepository.findById(1L)).thenReturn(Optional.of(club));
        when(clubRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(club));
        when(clubRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(otherClub));
        when(userRepository.getReferenceById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0)));
        when(memberRepository.findByClubIdAndUserId(1L, 18L))
                .thenReturn(Optional.of(member(1L, club, 18L, ClubMemberRole.PRESIDENT, ClubMemberStatus.ACTIVE)));
        when(memberRepository.findByClubIdAndUserId(2L, 20L))
                .thenReturn(Optional.of(member(4L, otherClub, 20L, ClubMemberRole.PRESIDENT,
                        ClubMemberStatus.ACTIVE)));
    }

    @Nested
    @DisplayName("Gửi yêu cầu")
    class Join {

        @Test
        @DisplayName("Lần đầu: tạo dòng PENDING / MEMBER")
        void first() {
            service.join(STUDENT, 1L);

            ArgumentCaptor<ClubMember> saved = ArgumentCaptor.forClass(ClubMember.class);
            verify(memberRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getId()).isNull();
            assertThat(saved.getValue().getStatus()).isEqualTo(ClubMemberStatus.PENDING);
            assertThat(saved.getValue().getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
            assertThat(saved.getValue().getUser().getId()).isEqualTo(4L);
        }

        @Test
        @DisplayName("Sau REJECTED: cập nhật chính dòng cũ (không chèn dòng mới, không trùng uq_club_members)")
        void afterRejected() {
            ClubMember rejected = member(9L, club, 4L, ClubMemberRole.MEMBER, ClubMemberStatus.REJECTED);
            when(memberRepository.findByClubIdAndUserId(1L, 4L)).thenReturn(Optional.of(rejected));

            service.join(STUDENT, 1L);

            verify(memberRepository).saveAndFlush(rejected);
            assertThat(rejected.getId()).isEqualTo(9L);
            assertThat(rejected.getStatus()).isEqualTo(ClubMemberStatus.PENDING);
        }

        @Test
        @DisplayName("Đang PENDING hoặc ACTIVE: không gửi lại")
        void duplicate() {
            when(memberRepository.findByClubIdAndUserId(1L, 4L))
                    .thenReturn(Optional.of(member(9L, club, 4L, ClubMemberRole.MEMBER, ClubMemberStatus.PENDING)));
            assertThatThrownBy(() -> service.join(STUDENT, 1L)).hasMessage(ClubMessages.ALREADY_PENDING);

            when(memberRepository.findByClubIdAndUserId(1L, 4L))
                    .thenReturn(Optional.of(member(9L, club, 4L, ClubMemberRole.MEMBER, ClubMemberStatus.ACTIVE)));
            assertThatThrownBy(() -> service.join(STUDENT, 1L)).hasMessage(ClubMessages.ALREADY_MEMBER);
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("CLB INACTIVE: chặn; giảng viên, phụ huynh: 403")
        void inactiveAndRoles() {
            club.setStatus(ClubStatus.INACTIVE);
            assertThatThrownBy(() -> service.join(STUDENT, 1L)).isInstanceOf(BusinessException.class)
                    .hasMessage(ClubMessages.CLUB_INACTIVE);
            assertThatThrownBy(() -> service.join(TEACHER, 1L)).isInstanceOf(AccessDeniedException.class);
            AppUserPrincipal parent = CatalogTestData.principal(5L, Set.of(RoleCodes.PARENT), Map.of());
            assertThatThrownBy(() -> service.join(parent, 1L)).isInstanceOf(AccessDeniedException.class);
            verify(memberRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Duyệt và từ chối")
    class Review {

        private ClubMember request;

        @BeforeEach
        void pending() {
            request = member(9L, club, 4L, ClubMemberRole.MEMBER, ClubMemberStatus.PENDING);
            when(memberRepository.findById(9L)).thenReturn(Optional.of(request));
        }

        @Test
        @DisplayName("Chủ nhiệm duyệt: ACTIVE, joined_at, khóa CLB trước, người gửi nhận thông báo, có audit")
        void approve() {
            service.approve(PRESIDENT, 1L, 9L, "127.0.0.1");

            assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            assertThat(request.getJoinedAt()).isNotNull();
            verify(clubRepository).findByIdForUpdate(1L);
            verify(notificationService).sendAutomatic(eq(18L), any(), any(), eq(NotificationPriority.NORMAL),
                    eq(NotificationTargetType.USER), eq(4L), eq(List.of(4L)));
            assertThat(audit().getAction()).isEqualTo(AuditActions.APPROVE_CLUB_MEMBER);
        }

        @Test
        @DisplayName("ADMIN từ chối: REJECTED, thông báo mức Quan trọng")
        void reject() {
            service.reject(ADMIN, 1L, 9L, null);

            assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.REJECTED);
            verify(notificationService).sendAutomatic(eq(1L), any(), any(), eq(NotificationPriority.IMPORTANT),
                    eq(NotificationTargetType.USER), eq(4L), eq(List.of(4L)));
            assertThat(audit().getAction()).isEqualTo(AuditActions.REJECT_CLUB_MEMBER);
        }

        @Test
        @DisplayName("Sinh viên không phải chủ nhiệm, phó chủ nhiệm, giảng viên: 403, không khóa, không đổi")
        void notPresident() {
            when(memberRepository.findByClubIdAndUserId(1L, 19L)).thenReturn(Optional.of(
                    member(2L, club, 19L, ClubMemberRole.VICE_PRESIDENT, ClubMemberStatus.ACTIVE)));
            AppUserPrincipal vice = CatalogTestData.principal(19L, Set.of(RoleCodes.STUDENT), Map.of());

            for (AppUserPrincipal actor : List.of(STUDENT, vice, TEACHER)) {
                assertThatThrownBy(() -> service.approve(actor, 1L, 9L, null))
                        .isInstanceOf(AccessDeniedException.class);
            }
            verify(clubRepository, never()).findByIdForUpdate(anyLong());
            assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.PENDING);
        }

        @Test
        @DisplayName("Chủ nhiệm CLB 2 duyệt yêu cầu của CLB 1 (qua đường dẫn CLB 1 hoặc CLB 2): 403")
        void otherClubPresident() {
            assertThatThrownBy(() -> service.approve(OTHER_PRESIDENT, 1L, 9L, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.approve(OTHER_PRESIDENT, 2L, 9L, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.PENDING);
        }

        @Test
        @DisplayName("Yêu cầu không còn PENDING hoặc CLB INACTIVE: báo lỗi, không gửi thông báo")
        void notPending() {
            request.setStatus(ClubMemberStatus.ACTIVE);
            assertThatThrownBy(() -> service.approve(PRESIDENT, 1L, 9L, null))
                    .hasMessage(ClubMessages.MEMBER_NOT_PENDING);

            request.setStatus(ClubMemberStatus.PENDING);
            club.setStatus(ClubStatus.INACTIVE);
            assertThatThrownBy(() -> service.reject(PRESIDENT, 1L, 9L, null)).hasMessage(ClubMessages.CLUB_INACTIVE);
            verify(notificationService, never()).sendAutomatic(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Đổi vai trò")
    class Role {

        @Test
        @DisplayName("Chủ nhiệm đổi MEMBER -> VICE_PRESIDENT và ngược lại, có audit")
        void toggle() {
            ClubMember member = member(2L, club, 19L, ClubMemberRole.MEMBER, ClubMemberStatus.ACTIVE);
            when(memberRepository.findById(2L)).thenReturn(Optional.of(member));

            service.changeRole(PRESIDENT, 1L, 2L, ClubMemberRole.VICE_PRESIDENT, null);
            assertThat(member.getMemberRole()).isEqualTo(ClubMemberRole.VICE_PRESIDENT);
            service.changeRole(PRESIDENT, 1L, 2L, ClubMemberRole.MEMBER, null);
            assertThat(member.getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
        }

        @Test
        @DisplayName("Không đặt ai làm PRESIDENT, không đổi vai trò chủ nhiệm, không đổi người chưa ACTIVE")
        void guards() {
            ClubMember member = member(2L, club, 19L, ClubMemberRole.MEMBER, ClubMemberStatus.ACTIVE);
            ClubMember president = member(1L, club, 18L, ClubMemberRole.PRESIDENT, ClubMemberStatus.ACTIVE);
            ClubMember pending = member(3L, club, 20L, ClubMemberRole.MEMBER, ClubMemberStatus.PENDING);
            when(memberRepository.findById(2L)).thenReturn(Optional.of(member));
            when(memberRepository.findById(1L)).thenReturn(Optional.of(president));
            when(memberRepository.findById(3L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.changeRole(PRESIDENT, 1L, 2L, ClubMemberRole.PRESIDENT, null))
                    .hasMessage(ClubMessages.ROLE_INVALID);
            assertThatThrownBy(() -> service.changeRole(ADMIN, 1L, 1L, ClubMemberRole.MEMBER, null))
                    .hasMessage(ClubMessages.PRESIDENT_ROLE_LOCKED);
            assertThatThrownBy(() -> service.changeRole(PRESIDENT, 1L, 3L, ClubMemberRole.VICE_PRESIDENT, null))
                    .hasMessage(ClubMessages.MEMBER_NOT_ACTIVE);
            assertThat(member.getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
        }
    }

    @Nested
    @DisplayName("Rời CLB")
    class Leave {

        @Test
        @DisplayName("Thành viên rời: LEFT, vai trò về MEMBER, hủy đăng ký hoạt động chưa bắt đầu, có audit")
        void memberLeaves() {
            ClubMember vice = member(2L, club, 19L, ClubMemberRole.VICE_PRESIDENT, ClubMemberStatus.ACTIVE);
            when(memberRepository.findByClubIdAndUserId(1L, 19L)).thenReturn(Optional.of(vice));
            when(participantRepository.cancelFutureRegistrations(eq(1L), eq(19L), any())).thenReturn(2);
            AppUserPrincipal actor = CatalogTestData.principal(19L, Set.of(RoleCodes.STUDENT), Map.of());

            assertThat(service.leave(actor, 1L, null)).isEqualTo(2);

            assertThat(vice.getStatus()).isEqualTo(ClubMemberStatus.LEFT);
            assertThat(vice.getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
            InOrder order = inOrder(clubRepository, participantRepository);
            order.verify(clubRepository).findByIdForUpdate(1L);
            order.verify(participantRepository).cancelFutureRegistrations(eq(1L), eq(19L), any());
            assertThat(audit().getDetail()).contains("cancelledRegistrations=2");
        }

        @Test
        @DisplayName("Chủ nhiệm không tự rời khi chưa chuyển giao; người không phải thành viên ACTIVE: báo lỗi")
        void presidentAndOutsider() {
            assertThatThrownBy(() -> service.leave(PRESIDENT, 1L, null))
                    .hasMessage(ClubMessages.PRESIDENT_CANNOT_LEAVE);
            assertThatThrownBy(() -> service.leave(STUDENT, 1L, null)).hasMessage(ClubMessages.NOT_MEMBER);
            verify(participantRepository, never()).cancelFutureRegistrations(anyLong(), anyLong(), any());
        }
    }

    private AuditLog audit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    static Club club(Long id, ClubStatus status) {
        Club club = new Club();
        club.setId(id);
        club.setCode("C" + id);
        club.setName("CLB " + id);
        club.setStatus(status);
        return club;
    }

    static ClubMember member(Long id, Club club, Long userId, ClubMemberRole role, ClubMemberStatus status) {
        ClubMember member = new ClubMember();
        member.setId(id);
        member.setClub(club);
        member.setUser(user(userId));
        member.setMemberRole(role);
        member.setStatus(status);
        return member;
    }

    static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setFullName("Người " + id);
        user.setActive(true);
        return user;
    }
}
