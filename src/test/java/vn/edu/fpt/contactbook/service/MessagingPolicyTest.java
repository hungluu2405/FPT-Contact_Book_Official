package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.MessagingContactRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bảng quy tắc ai nhắn cho ai: người gửi users.id 10, người nhận users.id 20. teachesStudentNow / teachesChildNow
 * giả lập quan hệ "đang dạy ở học kỳ hiện tại"; false nghĩa là chưa từng dạy hoặc học kỳ đã kết thúc.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessagingPolicyTest {

    static final long ME = 10L;
    static final long OTHER = 20L;
    static final Set<String> STUDENT = Set.of(RoleCodes.STUDENT);
    static final Set<String> PARENT = Set.of(RoleCodes.PARENT);
    static final Set<String> TEACHER = Set.of(RoleCodes.TEACHER);
    static final Set<String> HEAD = Set.of(RoleCodes.HEAD_OF_DEPARTMENT);
    static final Set<String> ADMIN = Set.of(RoleCodes.ADMIN);

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock MessagingContactRepository contactRepository;

    private MessagingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MessagingPolicy(userRepository, userRoleRepository, contactRepository);
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, true)));
    }

    static Stream<Arguments> rules() {
        return Stream.of(
                // người gửi, người nhận, đang dạy SV, đang dạy con PH, kết quả
                Arguments.of("SV -> GV đang dạy", STUDENT, TEACHER, true, false, true),
                Arguments.of("SV -> GV hết học kỳ / không dạy", STUDENT, TEACHER, false, false, false),
                Arguments.of("SV -> TBM đang dạy", STUDENT, HEAD, true, false, true),
                Arguments.of("GV -> SV đang dạy", TEACHER, STUDENT, true, false, true),
                Arguments.of("GV -> SV hết học kỳ / không dạy", TEACHER, STUDENT, false, false, false),
                Arguments.of("PH -> GV đang dạy con", PARENT, TEACHER, false, true, true),
                Arguments.of("PH -> GV không dạy con", PARENT, TEACHER, false, false, false),
                Arguments.of("GV -> PH có con đang học", TEACHER, PARENT, false, true, true),
                Arguments.of("GV -> PH hết học kỳ / không dạy con", TEACHER, PARENT, false, false, false),
                Arguments.of("GV -> GV", TEACHER, TEACHER, false, false, true),
                Arguments.of("GV -> TBM", TEACHER, HEAD, false, false, true),
                Arguments.of("TBM -> GV", HEAD, TEACHER, false, false, true),
                Arguments.of("SV -> SV", STUDENT, STUDENT, true, true, false),
                Arguments.of("PH -> PH", PARENT, PARENT, true, true, false),
                Arguments.of("SV -> PH", STUDENT, PARENT, true, true, false),
                Arguments.of("PH -> SV", PARENT, STUDENT, true, true, false),
                Arguments.of("ADMIN -> GV", ADMIN, TEACHER, true, true, false),
                Arguments.of("GV -> ADMIN", TEACHER, ADMIN, true, true, false),
                Arguments.of("ADMIN -> SV", ADMIN, STUDENT, true, true, false),
                Arguments.of("ADMIN + GV -> GV", Set.of(RoleCodes.ADMIN, RoleCodes.TEACHER), TEACHER, false, false,
                        true),
                Arguments.of("GV + PH -> GV (vai trò giảng viên)", Set.of(RoleCodes.TEACHER, RoleCodes.PARENT),
                        TEACHER, false, false, true),
                Arguments.of("PH -> GV + SV, GV đang dạy con", PARENT, Set.of(RoleCodes.TEACHER, RoleCodes.STUDENT),
                        false, true, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("Bảng quy tắc theo cặp vai trò")
    void rule(String name, Set<String> myRoles, Set<String> otherRoles, boolean teachesStudent, boolean teachesChild,
              boolean expected) {
        stubRoles(otherRoles);
        when(contactRepository.teachesStudentNow(anyLong(), anyLong())).thenReturn(teachesStudent);
        when(contactRepository.teachesChildNow(anyLong(), anyLong())).thenReturn(teachesChild);

        assertThat(policy.canMessage(principal(myRoles), OTHER)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Quan hệ được hỏi đúng chiều: giảng viên là bên nhận khi SV / PH gửi")
    void relationDirection() {
        stubRoles(TEACHER);
        when(contactRepository.teachesStudentNow(OTHER, ME)).thenReturn(true);
        assertThat(policy.canMessage(principal(STUDENT), OTHER)).isTrue();

        when(contactRepository.teachesChildNow(OTHER, ME)).thenReturn(true);
        assertThat(policy.canMessage(principal(PARENT), OTHER)).isTrue();

        stubRoles(STUDENT);
        when(contactRepository.teachesStudentNow(ME, OTHER)).thenReturn(true);
        assertThat(policy.canMessage(principal(TEACHER), OTHER)).isTrue();
    }

    @Test
    @DisplayName("Không tự nhắn cho mình")
    void notSelf() {
        assertThat(policy.canMessage(principal(TEACHER), ME)).isFalse();
        verify(userRepository, never()).findById(ME);
    }

    @Test
    @DisplayName("Người nhận bị khóa hoặc không tồn tại: không gửi được, kể cả GV -> GV")
    void inactiveOrMissing() {
        stubRoles(TEACHER);
        when(userRepository.findById(OTHER)).thenReturn(Optional.of(user(OTHER, false)));
        assertThat(policy.canMessage(principal(TEACHER), OTHER)).isFalse();

        when(userRepository.findById(OTHER)).thenReturn(Optional.empty());
        assertThat(policy.canMessage(principal(TEACHER), OTHER)).isFalse();
        assertThat(policy.canMessage(principal(TEACHER), null)).isFalse();
    }

    @Test
    @DisplayName("Vai trò dùng tin nhắn: SV, PH, GV, TBM; chỉ ADMIN thì không")
    void usesMessaging() {
        assertThat(MessagingPolicy.usesMessaging(STUDENT)).isTrue();
        assertThat(MessagingPolicy.usesMessaging(PARENT)).isTrue();
        assertThat(MessagingPolicy.usesMessaging(TEACHER)).isTrue();
        assertThat(MessagingPolicy.usesMessaging(HEAD)).isTrue();
        assertThat(MessagingPolicy.usesMessaging(ADMIN)).isFalse();
        assertThat(MessagingPolicy.usesMessaging(Set.of())).isFalse();
    }

    private void stubRoles(Set<String> roles) {
        when(userRoleRepository.findByUserId(OTHER)).thenReturn(roles.stream().map(code -> {
            Role role = new Role();
            role.setCode(code);
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            return userRole;
        }).toList());
    }

    static AppUserPrincipal principal(Set<String> roles) {
        return CatalogTestData.principal(ME, roles, Map.of());
    }

    static User user(Long id, boolean active) {
        User user = new User();
        user.setId(id);
        user.setActive(active);
        return user;
    }
}
