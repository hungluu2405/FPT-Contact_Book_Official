package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;

@Repository
public interface ClubActivityParticipantRepository extends JpaRepository<ClubActivityParticipant, ClubActivityParticipantId> {
}
