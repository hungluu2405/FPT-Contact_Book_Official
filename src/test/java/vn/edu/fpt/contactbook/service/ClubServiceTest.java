package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.dto.ClubForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.club;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.member;
import static vn.edu.fpt.contactbook.service.ClubMembershipServiceTest.user;

/**
 * Quản lý CLB (ADMIN): kiểm tra form (mã trùng, ngày thành lập, giảng viên phụ trách), logo, ngừng / mở lại, chỉ định
 * và chuyển giao chủ nhiệm (đúng một PRESIDENT, dùng lại dòng cũ, người mới phải là sinh viên đang hoạt động).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClubServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    @Mock ClubRepository clubRepository;
    @Mock ClubMemberRepository memberRepository;
    @Mock StudentRepository studentRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock AuditLogRepository auditLogRepository;
    @TempDir Path uploadDir;

    private ClubService service;
    private Club club;

    @BeforeEach
    void setUp() {
        service = new ClubService(clubRepository, memberRepository, studentRepository, userRepository,
                userRoleRepository, new ClubLogoStorage(uploadDir.toString()), new ClubAccessPolicy(memberRepository),
                new UserAccessPolicy(teacherRepository), new AuditService(auditLogRepository, userRepository));
        club = club(1L, ClubStatus.ACTIVE);
        when(clubRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(club));
        when(clubRepository.findWithAdvisor(1L)).thenReturn(Optional.of(club));
        when(clubRepository.saveAndFlush(any(Club.class))).thenAnswer(inv -> {
            Club c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(7L);
            }
            return c;
        });
        when(userRepository.getReferenceById(anyLong())).thenAnswer(inv -> user(inv.getArgument(0)));
        when(userRoleRepository.isActiveWithRole(eq(16L), anyCollection())).thenReturn(true);
    }

    @Nested
    @DisplayName("Thêm, sửa, ngừng hoạt động")
    class Manage {

        @Test
        @DisplayName("Thêm: mã chuẩn hóa viết hoa, giảng viên phụ trách hợp lệ, logo lưu tên UUID, có audit")
        void create() throws Exception {
            ClubForm form = form(" f-chess ", 16L);

            Long id = service.create(ADMIN, form, new AvatarUpload("image/png", PNG), "127.0.0.1");

            ArgumentCaptor<Club> saved = ArgumentCaptor.forClass(Club.class);
            verify(clubRepository).saveAndFlush(saved.capture());
            assertThat(id).isEqualTo(7L);
            assertThat(saved.getValue().getCode()).isEqualTo("F-CHESS");
            assertThat(saved.getValue().getAdvisor().getId()).isEqualTo(16L);
            assertThat(saved.getValue().getLogoUrl()).matches("/club-logos/[0-9a-f-]{36}\\.png");
            try (var files = Files.list(uploadDir.resolve("club-logos"))) {
                assertThat(files).hasSize(1);
            }
            assertThat(audit().getAction()).isEqualTo(AuditActions.CREATE_CLUB);
        }

        @Test
        @DisplayName("Mã trùng, ngày thành lập tương lai, giảng viên phụ trách không hợp lệ: lỗi theo ô, không lưu")
        void invalid() {
            when(clubRepository.existsByCode("F-CODE")).thenReturn(true);
            ClubForm form = form("F-CODE", 4L);
            form.setFoundedDate(AppTime.today().plusDays(1));

            assertThatThrownBy(() -> service.create(ADMIN, form, null, null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsEntry("code", ClubMessages.CODE_TAKEN)
                            .containsEntry("foundedDate", ClubMessages.FOUNDED_IN_FUTURE)
                            .containsEntry("advisorUserId", ClubMessages.ADVISOR_INVALID));
            verify(clubRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Logo sai loại hoặc quá 2 MB: lỗi ô logo, không lưu CLB")
        void badLogo() {
            assertThatThrownBy(() -> service.create(ADMIN, form("F-CHESS", null),
                    new AvatarUpload("image/png", "GIF89a".getBytes()), null))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                            .containsKey("logo"));
            byte[] tooBig = new byte[ImageStore.MAX_BYTES + 1];
            System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
            assertThatThrownBy(() -> service.create(ADMIN, form("F-CHESS", null),
                    new AvatarUpload("image/png", tooBig), null)).hasMessageContaining("2 MB");
            verify(clubRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Ngừng hoạt động rồi mở lại, có audit; lặp lại trạng thái cũ thì báo lỗi")
        void status() {
            service.deactivate(ADMIN, 1L, null);
            assertThat(club.getStatus()).isEqualTo(ClubStatus.INACTIVE);
            assertThatThrownBy(() -> service.deactivate(ADMIN, 1L, null)).hasMessage(ClubMessages.ALREADY_INACTIVE);
            service.activate(ADMIN, 1L, null);
            assertThat(club.getStatus()).isEqualTo(ClubStatus.ACTIVE);
        }

        @Test
        @DisplayName("Người không phải ADMIN: 403")
        void adminOnly() {
            assertThatThrownBy(() -> service.create(STUDENT, form("X", null), null, null))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.assignPresident(STUDENT, 1L, "SE190001", null))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Chỉ định và chuyển giao chủ nhiệm")
    class President {

        private ClubMember oldPresident;

        @BeforeEach
        void seed() {
            oldPresident = member(1L, club, 18L, ClubMemberRole.PRESIDENT, ClubMemberStatus.ACTIVE);
            when(memberRepository.findPresidents(1L)).thenReturn(List.of(oldPresident));
            Student student = new Student();
            student.setUser(user(4L));
            when(studentRepository.findByStudentCode("SE190001")).thenReturn(Optional.of(student));
            when(userRoleRepository.isActiveWithRole(4L, List.of(RoleCodes.STUDENT))).thenReturn(true);
        }

        @Test
        @DisplayName("Người mới chưa có dòng: tạo ACTIVE / PRESIDENT; chủ nhiệm cũ thành MEMBER; có audit from/to")
        void transferToNewcomer() {
            service.assignPresident(ADMIN, 1L, " se190001 ", null);

            ArgumentCaptor<ClubMember> saved = ArgumentCaptor.forClass(ClubMember.class);
            verify(memberRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getUser().getId()).isEqualTo(4L);
            assertThat(saved.getValue().getMemberRole()).isEqualTo(ClubMemberRole.PRESIDENT);
            assertThat(saved.getValue().getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            assertThat(saved.getValue().getJoinedAt()).isNotNull();
            assertThat(oldPresident.getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
            assertThat(oldPresident.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            verify(clubRepository).findByIdForUpdate(1L);
            assertThat(audit().getDetail()).isEqualTo("from=18, to=4");
        }

        @Test
        @DisplayName("Người mới đã có dòng (LEFT): cập nhật chính dòng đó (uq_club_members), không tạo dòng mới")
        void reuseRow() {
            ClubMember left = member(9L, club, 4L, ClubMemberRole.MEMBER, ClubMemberStatus.LEFT);
            when(memberRepository.findByClubIdAndUserId(1L, 4L)).thenReturn(Optional.of(left));

            service.assignPresident(ADMIN, 1L, "SE190001", null);

            verify(memberRepository).saveAndFlush(left);
            assertThat(left.getId()).isEqualTo(9L);
            assertThat(left.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            assertThat(left.getMemberRole()).isEqualTo(ClubMemberRole.PRESIDENT);
        }

        @Test
        @DisplayName("Không phải sinh viên đang hoạt động, mã không tồn tại, đã là chủ nhiệm: báo lỗi, không đổi ai")
        void guards() {
            when(userRoleRepository.isActiveWithRole(4L, List.of(RoleCodes.STUDENT))).thenReturn(false);
            assertThatThrownBy(() -> service.assignPresident(ADMIN, 1L, "SE190001", null))
                    .hasMessage(ClubMessages.PRESIDENT_NOT_STUDENT);
            assertThatThrownBy(() -> service.assignPresident(ADMIN, 1L, "SE999999", null))
                    .hasMessage(ClubMessages.STUDENT_NOT_FOUND);
            assertThatThrownBy(() -> service.assignPresident(ADMIN, 1L, " ", null))
                    .hasMessage(ClubMessages.STUDENT_CODE_REQUIRED);

            Student president = new Student();
            president.setUser(user(18L));
            when(studentRepository.findByStudentCode("SE180101")).thenReturn(Optional.of(president));
            when(userRoleRepository.isActiveWithRole(18L, List.of(RoleCodes.STUDENT))).thenReturn(true);
            assertThatThrownBy(() -> service.assignPresident(ADMIN, 1L, "SE180101", null))
                    .hasMessage(ClubMessages.ALREADY_PRESIDENT);

            assertThat(oldPresident.getMemberRole()).isEqualTo(ClubMemberRole.PRESIDENT);
            verify(memberRepository, never()).saveAndFlush(any());
        }
    }

    private AuditLog audit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    static ClubForm form(String code, Long advisorId) {
        ClubForm form = new ClubForm();
        form.setCode(code);
        form.setName("Câu lạc bộ Cờ vua");
        form.setDescription("Giao lưu cờ vua");
        form.setFoundedDate(AppTime.today().minusYears(1));
        form.setAdvisorUserId(advisorId);
        return form;
    }
}
