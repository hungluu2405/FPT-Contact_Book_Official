package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.UserRole;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role LEFT JOIN FETCH ur.department WHERE ur.user.id = :userId")
    List<UserRole> findByUserIdWithRoleAndDepartment(@Param("userId") Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    boolean existsByUserIdAndRoleCode(Long userId, String roleCode);
}
