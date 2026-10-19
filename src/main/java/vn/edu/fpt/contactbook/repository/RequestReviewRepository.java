package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.RequestReview;

import java.util.List;

@Repository
public interface RequestReviewRepository extends JpaRepository<RequestReview, Long> {

    /** Lịch sử duyệt của một đơn, cũ trước. */
    @EntityGraph(attributePaths = {"reviewer"})
    List<RequestReview> findByRequestIdOrderByReviewedAtAscIdAsc(Long requestId);
}
