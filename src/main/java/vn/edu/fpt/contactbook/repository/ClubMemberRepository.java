package vn.edu.fpt.contactbook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {

    String PRESIDENT_ACTIVE = """
            m.memberRole = vn.edu.fpt.contactbook.model.enums.ClubMemberRole.PRESIDENT
              AND m.status = vn.edu.fpt.contactbook.model.enums.ClubMemberStatus.ACTIVE
            """;

    Optional<ClubMember> findByClubIdAndUserId(Long clubId, Long userId);

    /** Chủ nhiệm đang hoạt động của CLB (bình thường đúng một dòng). */
    @Query("SELECT m FROM ClubMember m JOIN FETCH m.user WHERE m.club.id = :clubId AND " + PRESIDENT_ACTIVE)
    List<ClubMember> findPresidents(@Param("clubId") Long clubId);

    /** Chủ nhiệm của nhiều CLB một lần (kèm tài khoản). */
    @Query("SELECT m FROM ClubMember m JOIN FETCH m.user WHERE m.club.id IN :clubIds AND " + PRESIDENT_ACTIVE)
    List<ClubMember> findPresidentsOf(@Param("clubIds") Collection<Long> clubIds);

    /** Thành viên của CLB theo trạng thái: chủ nhiệm, phó chủ nhiệm, thành viên; rồi theo họ tên. */
    @Query(value = """
            SELECT m FROM ClubMember m JOIN FETCH m.user u
            WHERE m.club.id = :clubId AND m.status = :status
            ORDER BY CASE m.memberRole
                       WHEN vn.edu.fpt.contactbook.model.enums.ClubMemberRole.PRESIDENT THEN 0
                       WHEN vn.edu.fpt.contactbook.model.enums.ClubMemberRole.VICE_PRESIDENT THEN 1
                       ELSE 2 END, u.fullName, m.id
            """,
            countQuery = "SELECT COUNT(m) FROM ClubMember m WHERE m.club.id = :clubId AND m.status = :status")
    Page<ClubMember> findPageByClub(@Param("clubId") Long clubId, @Param("status") ClubMemberStatus status,
                                    Pageable pageable);

    /** Số thành viên ACTIVE của nhiều CLB một lần: [clubId, số thành viên]. */
    @Query("""
            SELECT m.club.id, COUNT(m) FROM ClubMember m
            WHERE m.club.id IN :clubIds AND m.status = vn.edu.fpt.contactbook.model.enums.ClubMemberStatus.ACTIVE
            GROUP BY m.club.id
            """)
    List<Object[]> countActiveByClubs(@Param("clubIds") Collection<Long> clubIds);

    /** "CLB của tôi": các dòng của một người theo trạng thái, kèm CLB, sắp theo tên CLB. */
    @Query("""
            SELECT m FROM ClubMember m JOIN FETCH m.club c
            WHERE m.user.id = :userId AND m.status IN :statuses
            ORDER BY c.name, c.id
            """)
    List<ClubMember> findByUser(@Param("userId") Long userId,
                                @Param("statuses") Collection<ClubMemberStatus> statuses);
}
