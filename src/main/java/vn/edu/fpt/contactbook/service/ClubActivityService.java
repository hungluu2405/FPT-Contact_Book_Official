package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ActivityForm;
import vn.edu.fpt.contactbook.dto.ActivityView;
import vn.edu.fpt.contactbook.dto.ClubAttendanceSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hoạt động câu lạc bộ (SRS FR-CLUB-05, 06, 07). Chủ nhiệm, phó chủ nhiệm của CLB đó hoặc ADMIN tạo, sửa, hủy
 * hoạt động (chỉ khi chưa bắt đầu; CLB ngừng hoạt động thì không tạo, sửa được) và ghi nhận tham dự (từ giờ bắt
 * đầu). Thành viên ACTIVE đăng ký (REGISTERED) và hủy đăng ký (CANCELLED) trước giờ bắt đầu; đăng ký lại sau khi hủy
 * dùng lại dòng cũ (khóa chính activity_id + user_id). Sĩ số đếm REGISTERED + ATTENDED sau khi khóa dòng hoạt động,
 * nên hai người không cùng lấy chỗ cuối. Trạng thái ONGOING / DONE tính lúc hiển thị (ClubRule.state).
 */
@Service
@RequiredArgsConstructor
public class ClubActivityService {

    private final ClubRepository clubRepository;
    private final ClubActivityRepository activityRepository;
    private final ClubActivityParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ClubAccessPolicy accessPolicy;
    private final AuditService auditService;

    /** Hoạt động sắp tới (chưa hủy, chưa kết thúc) của CLB, kèm đăng ký của người xem. */
    @Transactional(readOnly = true)
    public List<ActivityView> upcoming(AppUserPrincipal actor, Long clubId) {
        Club club = findClub(clubId);
        return views(actor, club, activityRepository.findUpcoming(clubId, ClubRule.now()));
    }

    /** Mọi hoạt động của CLB (trang quản lý hoạt động; chủ nhiệm, phó chủ nhiệm, ADMIN). */
    @Transactional(readOnly = true)
    public Page<ActivityView> all(AppUserPrincipal actor, Long clubId, int page) {
        accessPolicy.requireManageActivities(actor, clubId);
        Club club = findClub(clubId);
        Page<ClubActivity> activities = activityRepository.findByClubIdOrderByStartTimeDescIdDesc(clubId,
                CatalogSupport.page(page));
        Map<Long, ActivityView> byId = views(actor, club, activities.getContent()).stream()
                .collect(Collectors.toMap(ActivityView::id, v -> v));
        return activities.map(a -> byId.get(a.getId()));
    }

    /** Form tạo trống; người không quản lý được hoạt động → 403, CLB ngừng hoạt động → lỗi nghiệp vụ. */
    @Transactional(readOnly = true)
    public ActivityForm newForm(AppUserPrincipal actor, Long clubId) {
        accessPolicy.requireManageActivities(actor, clubId);
        requireActive(findClub(clubId));
        return new ActivityForm();
    }

    @Transactional(readOnly = true)
    public ActivityForm editForm(AppUserPrincipal actor, Long clubId, Long activityId) {
        accessPolicy.requireManageActivities(actor, clubId);
        ClubActivity activity = inClub(clubId, activityRepository.findById(activityId));
        ActivityForm form = new ActivityForm();
        form.setTitle(activity.getTitle());
        form.setDescription(activity.getDescription());
        form.setLocation(activity.getLocation());
        form.setStartTime(activity.getStartTime());
        form.setEndTime(activity.getEndTime());
        form.setMaxParticipants(activity.getMaxParticipants() == null ? null
                : activity.getMaxParticipants().intValue());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, Long clubId, ActivityForm form, String ipAddress) {
        accessPolicy.requireManageActivities(actor, clubId);
        Club club = requireActive(findClub(clubId));
        ClubActivity activity = new ClubActivity();
        activity.setClub(club);
        apply(activity, form, 0);
        activity.setStatus(ClubActivityStatus.PLANNED);
        activity.setCreatedBy(userRepository.getReferenceById(actor.getUserId()));
        save(activity);
        auditService.log(actor.getUserId(), AuditActions.CREATE_CLUB_ACTIVITY, AuditActions.ENTITY_CLUB_ACTIVITY,
                activity.getId(), "club=" + clubId + ", start=" + activity.getStartTime(), ipAddress);
        return activity.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long clubId, Long activityId, ActivityForm form, String ipAddress) {
        accessPolicy.requireManageActivities(actor, clubId);
        requireActive(findClub(clubId));
        ClubActivity activity = inClub(clubId, activityRepository.findByIdForUpdate(activityId));
        ClubRule.requireEditable(activity, ClubRule.now());
        apply(activity, form, participantRepository.countTaken(activityId));
        save(activity);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_CLUB_ACTIVITY, AuditActions.ENTITY_CLUB_ACTIVITY,
                activityId, "club=" + clubId + ", start=" + activity.getStartTime(), ipAddress);
    }

    /** Hủy hoạt động chưa bắt đầu; các dòng đăng ký giữ nguyên (hoạt động hiện "Đã hủy"). */
    @Transactional
    public void cancel(AppUserPrincipal actor, Long clubId, Long activityId, String ipAddress) {
        accessPolicy.requireManageActivities(actor, clubId);
        ClubActivity activity = inClub(clubId, activityRepository.findByIdForUpdate(activityId));
        ClubRule.requireEditable(activity, ClubRule.now());
        activity.setStatus(ClubActivityStatus.CANCELLED);
        auditService.log(actor.getUserId(), AuditActions.CANCEL_CLUB_ACTIVITY, AuditActions.ENTITY_CLUB_ACTIVITY,
                activityId, "club=" + clubId, ipAddress);
    }

    /** Đăng ký (thành viên ACTIVE của CLB; người khác → 403). */
    @Transactional
    public void register(AppUserPrincipal actor, Long clubId, Long activityId) {
        if (!ClubAccessPolicy.isActive(accessPolicy.membership(actor, clubId).orElse(null))) {
            throw new AccessDeniedException("Not an active member of this club");
        }
        requireActive(findClub(clubId));
        ClubActivity activity = inClub(clubId, activityRepository.findByIdForUpdate(activityId));
        LocalDateTime now = ClubRule.now();
        ClubRule.requireOpenForRegistration(activity, now);
        ClubActivityParticipantId id = new ClubActivityParticipantId(activityId, actor.getUserId());
        ClubActivityParticipant participant = participantRepository.findById(id).orElse(null);
        if (participant != null && participant.getStatus() != ParticipantStatus.CANCELLED) {
            throw new BusinessException(ClubMessages.ALREADY_REGISTERED);
        }
        ClubRule.requireCapacity(activity.getMaxParticipants(), participantRepository.countTaken(activityId));
        if (participant == null) {
            participant = new ClubActivityParticipant();
            participant.setId(id);
            participant.setActivity(activity);
            participant.setUser(userRepository.getReferenceById(actor.getUserId()));
        }
        participant.setStatus(ParticipantStatus.REGISTERED);
        participant.setRegisteredAt(now);
        participantRepository.saveAndFlush(participant);
    }

    /** Hủy đăng ký trước giờ bắt đầu. */
    @Transactional
    public void unregister(AppUserPrincipal actor, Long clubId, Long activityId) {
        ClubActivity activity = inClub(clubId, activityRepository.findById(activityId));
        ClubActivityParticipant participant = participantRepository
                .findById(new ClubActivityParticipantId(activityId, actor.getUserId()))
                .filter(p -> p.getStatus() == ParticipantStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(ClubMessages.NOT_REGISTERED));
        ClubRule.requireOpenForRegistration(activity, ClubRule.now());
        participant.setStatus(ParticipantStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public ClubAttendanceSheet attendanceSheet(AppUserPrincipal actor, Long clubId, Long activityId) {
        accessPolicy.requireManageActivities(actor, clubId);
        Club club = findClub(clubId);
        ClubActivity activity = inClub(clubId, activityRepository.findById(activityId));
        List<ClubAttendanceSheet.Row> rows = participantRepository.findTaken(activityId).stream()
                .map(p -> new ClubAttendanceSheet.Row(p.getUser().getId(), p.getUser().getFullName(),
                        p.getStatus() == ParticipantStatus.ATTENDED))
                .toList();
        return new ClubAttendanceSheet(views(actor, club, List.of(activity)).getFirst(), club.getName(), rows);
    }

    /**
     * Ghi nhận tham dự từ giờ bắt đầu: người được chọn thành ATTENDED, người đang giữ chỗ còn lại về REGISTERED.
     * Chọn người chưa đăng ký → lỗi, không lưu gì. Trả số người tham dự.
     */
    @Transactional
    public int markAttendance(AppUserPrincipal actor, Long clubId, Long activityId, Collection<Long> attendedUserIds,
                              String ipAddress) {
        accessPolicy.requireManageActivities(actor, clubId);
        ClubActivity activity = inClub(clubId, activityRepository.findById(activityId));
        ClubRule.requireCanMarkAttendance(activity, ClubRule.now());
        List<ClubActivityParticipant> participants = participantRepository.findTaken(activityId);
        Set<Long> chosen = attendedUserIds == null ? Set.of() : new HashSet<>(attendedUserIds);
        Set<Long> known = participants.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        if (!known.containsAll(chosen)) {
            throw new BusinessException(ClubMessages.PARTICIPANT_UNKNOWN);
        }
        for (ClubActivityParticipant p : participants) {
            p.setStatus(chosen.contains(p.getUser().getId()) ? ParticipantStatus.ATTENDED
                    : ParticipantStatus.REGISTERED);
        }
        auditService.log(actor.getUserId(), AuditActions.MARK_CLUB_ATTENDANCE, AuditActions.ENTITY_CLUB_ACTIVITY,
                activityId, "club=" + clubId + ", attended=" + chosen.size() + "/" + participants.size(), ipAddress);
        return chosen.size();
    }

    /** taken: số chỗ đang giữ (khi sửa, số tối đa mới không được nhỏ hơn). */
    private void apply(ClubActivity activity, ActivityForm form, long taken) {
        Map<String, String> errors = new LinkedHashMap<>();
        String title = CatalogSupport.requireText(form.getTitle(), 200, "title", ClubMessages.TITLE_REQUIRED,
                ClubMessages.TITLE_SIZE, errors);
        String description = CatalogSupport.optionalText(form.getDescription(), ClubRule.MAX_DESCRIPTION,
                "description", ClubMessages.DESCRIPTION_SIZE, errors);
        String location = CatalogSupport.optionalText(form.getLocation(), 200, "location",
                ClubMessages.LOCATION_SIZE, errors);
        LocalDateTime start = form.getStartTime();
        LocalDateTime end = form.getEndTime();
        if (start == null) {
            errors.put("startTime", ClubMessages.START_REQUIRED);
        } else if (!start.isAfter(ClubRule.now())) {
            errors.put("startTime", ClubMessages.START_IN_PAST);
        }
        if (end == null) {
            errors.put("endTime", ClubMessages.END_REQUIRED);
        } else if (start != null && !end.isAfter(start)) {
            errors.put("endTime", ClubMessages.END_AFTER_START);
        }
        Integer max = form.getMaxParticipants();
        if (max != null && (max < 1 || max > Short.MAX_VALUE)) {
            errors.put("maxParticipants", ClubMessages.MAX_RANGE);
        } else if (max != null && max < taken) {
            errors.put("maxParticipants", ClubMessages.MAX_BELOW_TAKEN.formatted(taken));
        }
        CatalogSupport.throwIfAny(errors);
        activity.setTitle(title);
        activity.setDescription(description);
        activity.setLocation(location);
        activity.setStartTime(start.withNano(0));
        activity.setEndTime(end.withNano(0));
        activity.setMaxParticipants(max == null ? null : max.shortValue());
    }

    /** Số chỗ và đăng ký của người xem cho cả danh sách, mỗi loại một truy vấn. */
    private List<ActivityView> views(AppUserPrincipal actor, Club club, List<ClubActivity> activities) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> ids = activities.stream().map(ClubActivity::getId).toList();
        Map<Long, Long> taken = new HashMap<>();
        for (Object[] r : participantRepository.countTakenByActivities(ids)) {
            taken.put((Long) r[0], ((Number) r[1]).longValue());
        }
        Map<Long, ParticipantStatus> mine = participantRepository
                .findByIdUserIdAndIdActivityIdIn(actor.getUserId(), ids).stream()
                .collect(Collectors.toMap(p -> p.getId().getActivityId(), ClubActivityParticipant::getStatus));
        ClubMember membership = accessPolicy.membership(actor, club.getId()).orElse(null);
        boolean member = ClubAccessPolicy.isActive(membership);
        boolean manage = accessPolicy.canManageActivities(actor, membership);
        boolean clubActive = club.getStatus() == ClubStatus.ACTIVE;
        LocalDateTime now = ClubRule.now();
        return activities.stream().map(a -> {
            ClubActivityStatus state = ClubRule.state(a, now);
            long count = taken.getOrDefault(a.getId(), 0L);
            ParticipantStatus my = mine.get(a.getId());
            boolean open = state == ClubActivityStatus.PLANNED;
            boolean hasRoom = a.getMaxParticipants() == null || count < a.getMaxParticipants();
            return new ActivityView(a.getId(), club.getId(), a.getTitle(), a.getDescription(), a.getLocation(),
                    a.getStartTime(), a.getEndTime(), a.getMaxParticipants(), count, state, my,
                    member && clubActive && open && hasRoom && (my == null || my == ParticipantStatus.CANCELLED),
                    my == ParticipantStatus.REGISTERED && open,
                    manage && clubActive && open,
                    manage && (state == ClubActivityStatus.ONGOING || state == ClubActivityStatus.DONE));
        }).toList();
    }

    private void save(ClubActivity activity) {
        try {
            activityRepository.saveAndFlush(activity);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Club findClub(Long clubId) {
        return clubRepository.findById(clubId).orElseThrow(() -> new NotFoundException(ClubMessages.NOT_FOUND));
    }

    private static Club requireActive(Club club) {
        if (club.getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ClubMessages.CLUB_INACTIVE);
        }
        return club;
    }

    /** Hoạt động phải thuộc CLB trên đường dẫn; khác hoặc không tồn tại → 404. */
    private static ClubActivity inClub(Long clubId, Optional<ClubActivity> activity) {
        return activity.filter(a -> Objects.equals(a.getClub().getId(), clubId))
                .orElseThrow(() -> new NotFoundException(ClubMessages.ACTIVITY_NOT_FOUND));
    }
}
