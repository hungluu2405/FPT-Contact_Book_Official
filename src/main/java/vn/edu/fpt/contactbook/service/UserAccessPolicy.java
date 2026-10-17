package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Set;

/**
 * Phạm vi quản lý người dùng (CLAUDE.md mục 5): ADMIN toàn quyền; HEAD_OF_DEPARTMENT chỉ xem giảng viên
 * có teachers.department_id thuộc bộ môn gắn với vai trò HEAD trong user_roles. Vi phạm → 403.
 */
@Component
@RequiredArgsConstructor
public class UserAccessPolicy {

    private final TeacherRepository teacherRepository;

    public boolean isAdmin(AppUserPrincipal actor) {
        return actor.hasRole(RoleCodes.ADMIN);
    }

    public Set<Long> headDepartmentIds(AppUserPrincipal actor) {
        return actor.getDepartmentIdsByRole().getOrDefault(RoleCodes.HEAD_OF_DEPARTMENT, Set.of());
    }

    public void requireAdmin(AppUserPrincipal actor) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Admin only");
        }
    }

    public void requireCanList(AppUserPrincipal actor) {
        if (!isAdmin(actor) && !actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT)) {
            throw new AccessDeniedException("Not allowed to list users");
        }
    }

    /** Kiểm tra trước cả khi biết người dùng có tồn tại, để HEAD không dò được id ngoài phạm vi. */
    public void requireCanView(AppUserPrincipal actor, Long userId) {
        if (isAdmin(actor)) {
            return;
        }
        Set<Long> scope = headDepartmentIds(actor);
        boolean inScope = !scope.isEmpty() && teacherRepository.findByUserId(userId)
                .map(teacher -> scope.contains(teacher.getDepartment().getId()))
                .orElse(false);
        if (!inScope) {
            throw new AccessDeniedException("User outside department scope");
        }
    }
}
