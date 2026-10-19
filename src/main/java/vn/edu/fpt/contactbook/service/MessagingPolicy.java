package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.MessagingContactRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quy tắc ai nhắn tin cho ai (SRS FR-MSG-01), tính theo hợp mọi vai trò của hai bên:
 * <ul>
 *     <li>"Giảng viên" là TEACHER hoặc HEAD_OF_DEPARTMENT; giảng viên với giảng viên luôn được.</li>
 *     <li>Sinh viên với giảng viên đang dạy mình ở học kỳ hiện tại.</li>
 *     <li>Phụ huynh với giảng viên đang dạy một con đã liên kết ở học kỳ hiện tại.</li>
 *     <li>Sinh viên, phụ huynh với nhau: không. Chỉ có ADMIN: không dùng tin nhắn.</li>
 * </ul>
 * Người kia phải đang hoạt động. Quy tắc chỉ chặn gửi tin mới; hội thoại cũ vẫn đọc được (kiểm tra ở MessageService).
 */
@Component
@RequiredArgsConstructor
public class MessagingPolicy {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final MessagingContactRepository contactRepository;

    public static boolean usesMessaging(Set<String> roles) {
        return isTeacher(roles) || roles.contains(RoleCodes.STUDENT) || roles.contains(RoleCodes.PARENT);
    }

    public static boolean isTeacher(Set<String> roles) {
        return roles.contains(RoleCodes.TEACHER) || roles.contains(RoleCodes.HEAD_OF_DEPARTMENT);
    }

    /** Người đăng nhập có được gửi tin mới cho otherUserId không (người không tồn tại → false). */
    public boolean canMessage(AppUserPrincipal actor, Long otherUserId) {
        if (otherUserId == null || Objects.equals(actor.getUserId(), otherUserId)) {
            return false;
        }
        boolean otherActive = userRepository.findById(otherUserId).map(User::isActive).orElse(false);
        if (!otherActive) {
            return false;
        }
        Set<String> otherRoles = userRoleRepository.findByUserId(otherUserId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
        return allowed(actor.getUserId(), actor.getRoles(), otherUserId, otherRoles);
    }

    private boolean allowed(Long a, Set<String> aRoles, Long b, Set<String> bRoles) {
        if (!usesMessaging(aRoles) || !usesMessaging(bRoles)) {
            return false;
        }
        boolean aTeacher = isTeacher(aRoles);
        boolean bTeacher = isTeacher(bRoles);
        if (aTeacher && bTeacher) {
            return true;
        }
        return (aTeacher && teachesNow(a, b, bRoles)) || (bTeacher && teachesNow(b, a, aRoles));
    }

    /** Giảng viên teacher đang dạy người dùng other (là sinh viên đó, hoặc phụ huynh của một sinh viên đó). */
    private boolean teachesNow(Long teacher, Long other, Set<String> otherRoles) {
        return (otherRoles.contains(RoleCodes.STUDENT) && contactRepository.teachesStudentNow(teacher, other))
                || (otherRoles.contains(RoleCodes.PARENT) && contactRepository.teachesChildNow(teacher, other));
    }
}
