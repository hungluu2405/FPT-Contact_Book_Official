package vn.edu.fpt.contactbook.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    @EntityGraph(attributePaths = {"major", "schoolClass"})
    Optional<Student> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Student> findByStudentCode(String studentCode);

    boolean existsByStudentCode(String studentCode);

    boolean existsByStudentCodeAndIdNot(String studentCode, Long id);

    boolean existsBySchoolClassId(Long classId);

    @EntityGraph(attributePaths = {"user"})
    List<Student> findBySchoolClassIdOrderByStudentCodeAsc(Long classId);

    @EntityGraph(attributePaths = {"user"})
    List<Student> findByStudentCodeIn(Collection<String> studentCodes);

    /**
     * Sinh viên đang học (STUDYING) có thể ghi danh vào lớp học phần: chưa có dòng ghi danh ENROLLED hoặc
     * COMPLETED trong lớp (DROPPED vẫn hiện để ghi danh lại). keywordPattern theo mã SV hoặc họ tên.
     */
    @EntityGraph(attributePaths = {"user", "schoolClass"})
    @Query("""
            SELECT s FROM Student s
            WHERE s.status = vn.edu.fpt.contactbook.model.enums.StudentStatus.STUDYING
              AND (:keywordPattern IS NULL
                   OR LOWER(s.studentCode) LIKE :keywordPattern ESCAPE '\\'
                   OR LOWER(s.user.fullName) LIKE :keywordPattern ESCAPE '\\')
              AND NOT EXISTS (SELECT 1 FROM Enrollment e
                              WHERE e.student = s AND e.classSection.id = :sectionId
                                AND e.status <> vn.edu.fpt.contactbook.model.enums.EnrollmentStatus.DROPPED)
            ORDER BY s.studentCode
            """)
    List<Student> findCandidates(@Param("keywordPattern") String keywordPattern, @Param("sectionId") Long sectionId,
                                 Pageable pageable);

    /** Khóa dòng sinh viên để hai thao tác đặt phụ huynh chính không chạy chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);
}
