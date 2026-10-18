package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Teacher;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    @EntityGraph(attributePaths = {"department"})
    Optional<Teacher> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Teacher> findByTeacherCode(String teacherCode);

    boolean existsByTeacherCode(String teacherCode);

    boolean existsByTeacherCodeAndIdNot(String teacherCode, Long id);
}
