package vn.edu.fpt.contactbook.security;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Người dùng đã đăng nhập. Quyền là hợp của mọi vai trò trong user_roles (CLAUDE.md mục 5);
 * departmentIdsByRole giữ phạm vi bộ môn của từng vai trò (ví dụ HEAD_OF_DEPARTMENT → {1}).
 */
@Getter
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String phone;
    private final String fullName;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Set<String> roles;
    private final Map<String, Set<Long>> departmentIdsByRole;
    private final List<GrantedAuthority> authorities;
    private String password;

    public AppUserPrincipal(Long userId, String phone, String fullName, String password, boolean active,
                            boolean mustChangePassword, Set<String> roles,
                            Map<String, Set<Long>> departmentIdsByRole) {
        this.userId = userId;
        this.phone = phone;
        this.fullName = fullName;
        this.password = password;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.roles = Set.copyOf(roles);
        this.departmentIdsByRole = departmentIdsByRole.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        this.authorities = this.roles.stream()
                .sorted()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /** Bản sao dùng sau khi đổi mật khẩu thành công: không còn bị ép đổi, không giữ mật khẩu. */
    public AppUserPrincipal withPasswordChanged() {
        return new AppUserPrincipal(userId, phone, fullName, null, active, false, roles, departmentIdsByRole);
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
