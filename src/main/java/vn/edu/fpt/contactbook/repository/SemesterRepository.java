package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Semester;

import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByCode(String code);

    Optional<Semester> findByCurrentTrue();
}
