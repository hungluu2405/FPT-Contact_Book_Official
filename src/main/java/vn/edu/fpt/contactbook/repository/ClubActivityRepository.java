package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubActivity;

import java.util.List;

@Repository
public interface ClubActivityRepository extends JpaRepository<ClubActivity, Long> {

    List<ClubActivity> findByClubId(Long clubId);
}
