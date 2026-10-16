package vn.edu.fpt.contactbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.StoredFile;

import java.util.List;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByUploadedById(Long uploadedById);
}
