package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClassSection;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {

    Optional<ClassSection> findByCode(String code);

    List<ClassSection> findByTeacherId(Long teacherId);

    List<ClassSection> findBySemesterId(Long semesterId);
}
