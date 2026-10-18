package vn.edu.fpt.contactbook.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {

    /**
     * keywordPattern lấy từ LikePatterns (mã lớp, mã hoặc tên môn) hoặc null; semesterId, status null là tất cả.
     * scoped = true giới hạn vào lớp có môn thuộc scopeDepartmentIds (phạm vi của HEAD_OF_DEPARTMENT).
     */
    String SEARCH_FROM_WHERE = """
            FROM ClassSection c
            WHERE (:keywordPattern IS NULL
                   OR LOWER(c.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(c.subject.code) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(c.subject.name) LIKE :keywordPattern ESCAPE '\\')
              AND (:semesterId IS NULL OR c.semester.id = :semesterId)
              AND (:status IS NULL OR c.status = :status)
              AND (:scoped = FALSE OR c.subject.department.id IN :scopeDepartmentIds)
            """;

    boolean existsBySubjectId(Long subjectId);

    /** Các lớp học phần của học kỳ do một giảng viên dạy (lịch dạy, lịch thi của giảng viên). */
    @Query("SELECT c.id FROM ClassSection c WHERE c.teacher.id = :teacherId AND c.semester.id = :semesterId")
    List<Long> findIdsByTeacherAndSemester(@Param("teacherId") Long teacherId, @Param("semesterId") Long semesterId);

    @EntityGraph(attributePaths = {"subject", "semester", "teacher", "teacher.user"})
    @Query(value = "SELECT c " + SEARCH_FROM_WHERE + " ORDER BY c.semester.startDate DESC, c.code",
            countQuery = "SELECT COUNT(c) " + SEARCH_FROM_WHERE)
    Page<ClassSection> search(@Param("keywordPattern") String keywordPattern,
                              @Param("semesterId") Long semesterId,
                              @Param("status") SectionStatus status,
                              @Param("scoped") boolean scoped,
                              @Param("scopeDepartmentIds") Collection<Long> scopeDepartmentIds,
                              Pageable pageable);

    /** Khóa dòng lớp học phần (UPDLOCK) để kiểm tra sĩ số và ghi danh không chạy chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassSection c WHERE c.id = :id")
    Optional<ClassSection> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** Các mã khớp mẫu LIKE; prefixPattern lấy từ LikePatterns.startsWith. */
    @Query("SELECT c.code FROM ClassSection c WHERE c.code LIKE :prefixPattern ESCAPE '\\'")
    List<String> findCodesLike(@Param("prefixPattern") String prefixPattern);
}
