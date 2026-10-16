package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.RequestReview;

import java.util.List;

@Repository
public interface RequestReviewRepository extends JpaRepository<RequestReview, Long> {

    List<RequestReview> findByRequestId(Long requestId);
}
