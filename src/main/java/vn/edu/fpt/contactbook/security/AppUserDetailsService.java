package vn.edu.fpt.contactbook.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.repository.UserRoleRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nạp người dùng theo số điện thoại ĐÃ chuẩn hóa (PhoneAuthenticationProvider chuẩn hóa trước),
 * kèm vai trò và bộ môn: 2 câu truy vấn.
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());

        Set<String> roles = new HashSet<>();
        Map<String, Set<Long>> departmentIdsByRole = new HashMap<>();
        for (UserRole userRole : userRoles) {
            String code = userRole.getRole().getCode();
            roles.add(code);
            if (userRole.getDepartment() != null) {
                departmentIdsByRole.computeIfAbsent(code, key -> new HashSet<>())
                        .add(userRole.getDepartment().getId());
            }
        }

        return new AppUserPrincipal(user.getId(), user.getPhone(), user.getFullName(), user.getPasswordHash(),
                user.isActive(), user.isMustChangePassword(), roles, departmentIdsByRole);
    }
}
