package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.ParentStudentId;

import java.util.List;

@Repository
public interface ParentStudentRepository extends JpaRepository<ParentStudent, ParentStudentId> {

    List<ParentStudent> findByIdParentId(Long parentId);

    List<ParentStudent> findByIdStudentId(Long studentId);

    List<ParentStudent> findByParentUserId(Long userId);

    List<ParentStudent> findByStudentUserId(Long userId);
}
