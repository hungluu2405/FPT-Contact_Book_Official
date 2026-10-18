package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Set;

/**
 * Phạm vi quản lý môn học và đầu điểm (SRS FR-ORG-03): ADMIN mọi môn; HEAD_OF_DEPARTMENT chỉ môn thuộc
 * bộ môn gắn với vai trò HEAD trong user_roles. Vi phạm → 403.
 */
@Component
@RequiredArgsConstructor
public class SubjectAccessPolicy {

    private final UserAccessPolicy userAccessPolicy;

    public boolean isAdmin(AppUserPrincipal actor) {
        return userAccessPolicy.isAdmin(actor);
    }

    /** Bộ môn trưởng bộ môn được quản lý; rỗng nếu không phải trưởng bộ môn. */
    public Set<Long> headDepartmentIds(AppUserPrincipal actor) {
        return userAccessPolicy.headDepartmentIds(actor);
    }

    public void requireCanManage(AppUserPrincipal actor) {
        if (!isAdmin(actor) && !(actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT) && !headDepartmentIds(actor).isEmpty())) {
            throw new AccessDeniedException("Not allowed to manage subjects");
        }
    }

    public boolean canManageDepartment(AppUserPrincipal actor, Long departmentId) {
        return isAdmin(actor) || (departmentId != null && headDepartmentIds(actor).contains(departmentId));
    }

    public void requireDepartment(AppUserPrincipal actor, Long departmentId) {
        if (!canManageDepartment(actor, departmentId)) {
            throw new AccessDeniedException("Subject outside department scope");
        }
    }
}
