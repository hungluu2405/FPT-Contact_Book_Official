package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ClubReportRow;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Báo cáo CLB (SRS FR-REPORT-03, chỉ ADMIN): theo từng CLB, số thành viên ACTIVE và số hoạt động theo trạng thái
 * hiển thị, tính bằng ClubRule.state tại thời điểm xem (giống trang CLB, giả định 108).
 */
@Service
@RequiredArgsConstructor
public class ClubReportService {

    private final UserAccessPolicy userAccessPolicy;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubActivityRepository clubActivityRepository;

    /** status null là mọi CLB; sắp theo tên, phân trang. */
    @Transactional(readOnly = true)
    public Page<ClubReportRow> report(AppUserPrincipal actor, ClubStatus status, int page) {
        userAccessPolicy.requireAdmin(actor);
        Page<Club> clubs = clubRepository.search(null, status, CatalogSupport.page(page));
        List<Long> ids = clubs.map(Club::getId).getContent();
        if (ids.isEmpty()) {
            return clubs.map(club -> toRow(club, 0, Map.of()));
        }
        Map<Long, Long> members = clubMemberRepository.countActiveByClubs(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        LocalDateTime now = ClubRule.now();
        Map<Long, Map<ClubActivityStatus, Long>> activities = new HashMap<>();
        for (Object[] row : clubActivityRepository.findTimesByClubs(ids)) {
            ClubActivityStatus state = ClubRule.state((ClubActivityStatus) row[1], (LocalDateTime) row[2],
                    (LocalDateTime) row[3], now);
            activities.computeIfAbsent((Long) row[0], key -> new EnumMap<>(ClubActivityStatus.class))
                    .merge(state, 1L, Long::sum);
        }
        return clubs.map(club -> toRow(club, members.getOrDefault(club.getId(), 0L),
                activities.getOrDefault(club.getId(), Map.of())));
    }

    private static ClubReportRow toRow(Club club, long members, Map<ClubActivityStatus, Long> activities) {
        return new ClubReportRow(club.getId(), club.getCode(), club.getName(), club.getStatus(), members,
                activities.getOrDefault(ClubActivityStatus.PLANNED, 0L),
                activities.getOrDefault(ClubActivityStatus.ONGOING, 0L),
                activities.getOrDefault(ClubActivityStatus.DONE, 0L),
                activities.getOrDefault(ClubActivityStatus.CANCELLED, 0L));
    }
}
