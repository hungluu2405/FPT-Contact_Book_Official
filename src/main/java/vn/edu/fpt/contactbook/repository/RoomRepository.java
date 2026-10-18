package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("""
            SELECT r FROM Room r
            WHERE :keywordPattern IS NULL
               OR LOWER(r.code) LIKE :keywordPattern ESCAPE '\\'
               OR LOWER(r.name) LIKE :keywordPattern ESCAPE '\\'
               OR LOWER(r.building) LIKE :keywordPattern ESCAPE '\\'
            ORDER BY r.code
            """)
    Page<Room> search(@Param("keywordPattern") String keywordPattern, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
