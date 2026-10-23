package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.edu.fpt.contactbook.dto.ParentLinkForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.Parent;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.ParentStudentId;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ParentRepository;
import vn.edu.fpt.contactbook.repository.ParentStudentRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParentLinkServiceTest {

    private static final AppUserPrincipal ADMIN = UserServiceTest.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

    @Mock UserRepository userRepository;
    @Mock StudentRepository studentRepository;
    @Mock ParentRepository parentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock AuditLogRepository auditLogRepository;

    private ParentLinkService service;
    private Student student;
    private Parent father;
    private Parent mother;

    @BeforeEach
    void setUp() {
        service = new ParentLinkService(userRepository, studentRepository, parentRepository, parentStudentRepository,
                new UserAccessPolicy(mock(TeacherRepository.class)), new AuditService(auditLogRepository, userRepository));
        student = new Student();
        student.setId(30L);
        student.setUser(user(4L, "0900000004"));
        father = parent(10L, user(5L, "0900000005"));
        mother = parent(11L, user(6L, "0900000006"));
        when(studentRepository.findByUserId(4L)).thenReturn(Optional.of(student));
        when(studentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(student));
        when(userRepository.findByPhone("0900000006")).thenReturn(Optional.of(mother.getUser()));
        when(parentRepository.findByUserId(6L)).thenReturn(Optional.of(mother));
        when(parentStudentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("Thêm phụ huynh chính mới thì bỏ cờ chính của phụ huynh cũ")
    void newPrimaryParentClearsOldOne() {
        ParentStudent existing = link(father, true);
        when(parentStudentRepository.findByIdStudentId(30L)).thenReturn(List.of(existing));
        ParentLinkForm form = new ParentLinkForm();
        form.setOtherKey("+84900000006");
        form.setRelationship(ParentRelationship.MOTHER);
        form.setPrimary(true);

        service.linkParent(ADMIN, 4L, form, null);

        ArgumentCaptor<ParentStudent> saved = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentStudentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimary()).isTrue();
        assertThat(saved.getValue().getRelationship()).isEqualTo(ParentRelationship.MOTHER);
        assertThat(existing.isPrimary()).isFalse();
        verify(studentRepository).findByIdForUpdate(30L);
    }

    @Test
    @DisplayName("Đặt phụ huynh chính: đúng một người có cờ chính")
    void setPrimaryKeepsSinglePrimary() {
        ParentStudent fatherLink = link(father, true);
        ParentStudent motherLink = link(mother, false);
        when(parentStudentRepository.findByIdStudentId(30L)).thenReturn(List.of(fatherLink, motherLink));
        when(parentStudentRepository.findById(new ParentStudentId(11L, 30L))).thenReturn(Optional.of(motherLink));

        service.setPrimaryParent(ADMIN, 4L, 11L, null);

        assertThat(fatherLink.isPrimary()).isFalse();
        assertThat(motherLink.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("Liên kết đã có thì báo trùng; người không có hồ sơ phụ huynh thì báo lỗi")
    void rejectsDuplicateAndMissingProfile() {
        when(parentStudentRepository.existsById(new ParentStudentId(11L, 30L))).thenReturn(true);
        ParentLinkForm form = new ParentLinkForm();
        form.setOtherKey("0900000006");
        assertThatThrownBy(() -> service.linkParent(ADMIN, 4L, form, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserMessages.LINK_EXISTS);

        form.setOtherKey("0900000099");
        assertThatThrownBy(() -> service.linkParent(ADMIN, 4L, form, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getField()).isEqualTo("otherKey");
    }

    @Test
    @DisplayName("Người dùng không có hồ sơ học sinh thì không thêm phụ huynh được")
    void studentProfileRequired() {
        assertThatThrownBy(() -> service.linkParent(ADMIN, 99L, new ParentLinkForm(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hồ sơ học sinh");
    }

    private ParentStudent link(Parent parent, boolean primary) {
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setPrimary(primary);
        return link;
    }

    private static Parent parent(Long id, User user) {
        Parent parent = new Parent();
        parent.setId(id);
        parent.setUser(user);
        return parent;
    }

    private static User user(Long id, String phone) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        return user;
    }
}
