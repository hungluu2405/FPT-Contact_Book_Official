package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Major;

import java.util.List;

@Repository
public interface MajorRepository extends JpaRepository<Major, Long> {

    List<Major> findAllByOrderByCodeAsc();
}
