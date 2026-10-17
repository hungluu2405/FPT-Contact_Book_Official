package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
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
}
