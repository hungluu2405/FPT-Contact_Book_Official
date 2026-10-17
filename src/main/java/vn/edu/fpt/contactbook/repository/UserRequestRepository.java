package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.UserRequest;

@Repository
public interface UserRequestRepository extends JpaRepository<UserRequest, Long> {
}
