package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @EntityGraph(attributePaths = {"role", "department"})
    List<UserRole> findByUserId(Long userId);

    /** Vai trò của cả một trang người dùng trong một truy vấn (tránh N+1 ở danh sách). */
    @EntityGraph(attributePaths = {"role", "department"})
    List<UserRole> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    boolean existsByUserIdAndRoleCode(Long userId, String roleCode);

    boolean existsByUserIdAndRoleIdAndDepartmentId(Long userId, Long roleId, Long departmentId);

    boolean existsByUserIdAndRoleIdAndDepartmentIsNull(Long userId, Long roleId);

    @Query("SELECT COUNT(DISTINCT ur.user.id) FROM UserRole ur WHERE ur.role.code = 'ADMIN' AND ur.user.active = true")
    long countActiveAdmins();

    /** Tài khoản đang hoạt động và có ít nhất một trong các vai trò. */
    @Query("""
            SELECT COUNT(ur) > 0 FROM UserRole ur
            WHERE ur.user.id = :userId AND ur.user.active = true AND ur.role.code IN :roleCodes
            """)
    boolean isActiveWithRole(@Param("userId") Long userId, @Param("roleCodes") Collection<String> roleCodes);

    /** Người dùng đang hoạt động có một trong các vai trò, theo họ tên (ví dụ danh sách giảng viên phụ trách CLB). */
    @Query("""
            SELECT DISTINCT u FROM UserRole ur JOIN ur.user u
            WHERE u.active = true AND ur.role.code IN :roleCodes
            ORDER BY u.fullName, u.id
            """)
    List<User> findActiveUsersWithRole(@Param("roleCodes") Collection<String> roleCodes);
}
