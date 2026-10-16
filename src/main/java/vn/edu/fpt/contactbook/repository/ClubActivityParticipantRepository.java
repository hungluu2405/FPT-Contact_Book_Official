package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;

import java.util.List;

@Repository
public interface ClubActivityParticipantRepository extends JpaRepository<ClubActivityParticipant, ClubActivityParticipantId> {

    List<ClubActivityParticipant> findByIdActivityId(Long activityId);

    List<ClubActivityParticipant> findByIdUserId(Long userId);
}
