package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.dto.UserListRow;
import vn.edu.fpt.contactbook.model.User;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Phần FROM/WHERE dùng chung cho truy vấn danh sách và truy vấn đếm.
     * keywordPattern đã được escape (%, _, [) và bọc %...%; so khớp với ESCAPE '\'.
     * scoped = true giới hạn vào giảng viên thuộc scopeDepartmentIds (phạm vi của HEAD_OF_DEPARTMENT).
     */
    String SEARCH_FROM_WHERE = """
            FROM User u
            LEFT JOIN Student s ON s.user = u
            LEFT JOIN Teacher t ON t.user = u
            LEFT JOIN t.department d
            WHERE (:keywordPattern IS NULL
                   OR LOWER(u.fullName) LIKE :keywordPattern ESCAPE '\\'
                   OR u.phone LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(s.studentCode) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(t.teacherCode) LIKE :keywordPattern ESCAPE '\\'
                   OR u.phone = :normalizedPhone)
              AND (:roleCode IS NULL
                   OR EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u AND ur.role.code = :roleCode))
              AND (:departmentId IS NULL OR d.id = :departmentId)
              AND (:classId IS NULL OR s.schoolClass.id = :classId)
              AND (:active IS NULL OR u.active = :active)
              AND (:scoped = FALSE OR d.id IN :scopeDepartmentIds)
            """;

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query(value = "SELECT new vn.edu.fpt.contactbook.dto.UserListRow(u.id, u.phone, u.fullName, "
            + "u.active, s.studentCode, t.teacherCode, d.name) " + SEARCH_FROM_WHERE + " ORDER BY u.fullName, u.id",
            countQuery = "SELECT COUNT(u) " + SEARCH_FROM_WHERE)
    Page<UserListRow> search(@Param("keywordPattern") String keywordPattern,
                             @Param("normalizedPhone") String normalizedPhone,
                             @Param("roleCode") String roleCode,
                             @Param("departmentId") Long departmentId,
                             @Param("classId") Long classId,
                             @Param("active") Boolean active,
                             @Param("scoped") boolean scoped,
                             @Param("scopeDepartmentIds") Collection<Long> scopeDepartmentIds,
                             Pageable pageable);
}
