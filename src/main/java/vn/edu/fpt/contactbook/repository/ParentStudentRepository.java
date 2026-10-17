package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.ParentStudentId;

import java.util.List;

@Repository
public interface ParentStudentRepository extends JpaRepository<ParentStudent, ParentStudentId> {

    /** Các con đã liên kết của một phụ huynh (theo parents.id). */
    @EntityGraph(attributePaths = {"student", "student.user"})
    List<ParentStudent> findByIdParentId(Long parentId);

    /** Các phụ huynh đã liên kết của một sinh viên (theo students.id). */
    @EntityGraph(attributePaths = {"parent", "parent.user"})
    List<ParentStudent> findByIdStudentId(Long studentId);
}
