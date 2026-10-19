package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.RequestFile;
import vn.edu.fpt.contactbook.model.RequestFileId;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestFileRepository extends JpaRepository<RequestFile, RequestFileId> {

    @EntityGraph(attributePaths = {"file"})
    List<RequestFile> findByIdRequestIdOrderByIdFileIdAsc(Long requestId);

    @EntityGraph(attributePaths = {"file"})
    Optional<RequestFile> findByIdRequestIdAndIdFileId(Long requestId, Long fileId);
}
