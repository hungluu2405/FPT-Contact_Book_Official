package vn.edu.fpt.contactbook.security;

import vn.edu.fpt.contactbook.model.enums.RoleCodes;

import java.util.Collection;
import java.util.Optional;

/**
 * Trang chủ theo vai trò, ưu tiên ADMIN → HEAD_OF_DEPARTMENT/TEACHER → STUDENT → PARENT.
 */
public final class RoleHomeResolver {

    private RoleHomeResolver() {
    }

    public static Optional<String> homeFor(Collection<String> roles) {
        if (roles.contains(RoleCodes.ADMIN)) {
            return Optional.of("/admin");
        }
        if (roles.contains(RoleCodes.HEAD_OF_DEPARTMENT) || roles.contains(RoleCodes.TEACHER)) {
            return Optional.of("/teacher");
        }
        if (roles.contains(RoleCodes.STUDENT)) {
            return Optional.of("/student");
        }
        if (roles.contains(RoleCodes.PARENT)) {
            return Optional.of("/parent");
        }
        return Optional.empty();
    }
}
