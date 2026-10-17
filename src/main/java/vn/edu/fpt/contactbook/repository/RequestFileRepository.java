package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.RequestFile;
import vn.edu.fpt.contactbook.model.RequestFileId;

@Repository
public interface RequestFileRepository extends JpaRepository<RequestFile, RequestFileId> {
}
