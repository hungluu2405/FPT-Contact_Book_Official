package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.GradeAdjustForm;
import vn.edu.fpt.contactbook.dto.GradeAdjustView;
import vn.edu.fpt.contactbook.dto.GradeEntryForm;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.NotificationPriority;
import vn.edu.fpt.contactbook.model.enums.NotificationTargetType;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.util.AppTime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Điểm số của lớp học phần (SRS FR-GRADE-02, 04, 06, UC-06). Phạm vi qua AttendanceScope: giảng viên chỉ lớp mình
 * dạy; trưởng bộ môn lớp thuộc bộ môn mình; ADMIN mọi lớp.
 * <ul>
 *   <li>Giảng viên nhập điểm các ô CHƯA công bố; ô đã công bố bị khóa với giảng viên.</li>
 *   <li>Trưởng bộ môn hoặc ADMIN công bố mọi ô đã có điểm chưa công bố; ô trống giữ chưa công bố.</li>
 *   <li>Trưởng bộ môn hoặc ADMIN điều chỉnh ô đã công bố, bắt buộc lý do, audit cũ → mới.</li>
 * </ul>
 * Nhập và công bố bị chặn khi tổng trọng số đầu điểm của môn khác 100% (giả định 35). Mọi thao tác ghi khóa dòng
 * lớp học phần tới hết transaction nên nhập, nhập Excel, công bố của cùng lớp không chạy chồng nhau.
 */
@Service
@RequiredArgsConstructor
public class GradeService {

    private final AttendanceScope scope;
    private final ClassSectionRepository classSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final NotificationAudience audience;

    /** Dữ liệu bảng điểm đọc trong một transaction; grades theo khóa GradeSheet.key(enrollmentId, componentId). */
    record Loaded(ClassSection section, List<GradeComponent> components, List<Enrollment> enrollments,
                  Map<String, Grade> grades) {
    }

    /** Một ô cần ghi; score null là xóa điểm. */
    record CellUpdate(Enrollment enrollment, GradeComponent component, BigDecimal score) {
    }

    /** Bảng điểm cho giảng viên (Access.TEACHER) hoặc trưởng bộ môn, ADMIN (Access.MANAGER), gồm cả ô chưa công bố. */
    @Transactional(readOnly = true)
    public GradeSheet sheet(AppUserPrincipal actor, Access access, Long sectionId) {
        Loaded data = load(scope.section(actor, access, sectionId, false));
        List<GradeSheet.Row> rows = data.enrollments().stream().map(enrollment -> {
            Map<Long, BigDecimal> scores = new HashMap<>();
            List<GradeSheet.Cell> rowCells = data.components().stream().map(component -> {
                Grade grade = data.grades().get(GradeSheet.key(enrollment.getId(), component.getId()));
                if (grade != null) {
                    scores.put(component.getId(), grade.getScore());
                }
                return grade == null
                        ? new GradeSheet.Cell(null, enrollment.getId(), component.getId(), null, false, null)
                        : new GradeSheet.Cell(grade.getId(), enrollment.getId(), component.getId(), grade.getScore(),
                        grade.isPublished(), grade.getNote());
            }).toList();
            return new GradeSheet.Row(enrollment.getId(), enrollment.getStudent().getStudentCode(),
                    enrollment.getStudent().getUser().getFullName(), rowCells,
                    GradeRule.average(data.components(), scores));
        }).toList();
        BigDecimal total = data.components().stream().map(GradeComponent::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<GradeSheet.Component> components = data.components().stream()
                .map(c -> new GradeSheet.Component(c.getId(), c.getName(), c.getWeight(), c.getMaxScore()))
                .toList();
        return new GradeSheet(AttendanceViewService.toSectionView(data.section()), components, total,
                !components.isEmpty() && total.compareTo(GradeComponentService.HUNDRED) == 0, rows,
                gradeRepository.countPublishable(sectionId));
    }

    /**
     * Giảng viên lưu bảng điểm. Kiểm tra mọi ô trước; có ô sai thì không lưu ô nào (BusinessException với lỗi theo
     * ô "scores[key]"). Chỉ ghi ô có thay đổi. Trả số ô đã ghi.
     */
    @Transactional
    public int save(AppUserPrincipal actor, Long sectionId, GradeEntryForm form, String ipAddress) {
        return guarded(() -> {
            Loaded data = lockForEntry(actor, sectionId);
            Map<Long, Enrollment> enrollments = data.enrollments().stream()
                    .collect(Collectors.toMap(Enrollment::getId, Function.identity()));
            Map<Long, GradeComponent> components = data.components().stream()
                    .collect(Collectors.toMap(GradeComponent::getId, Function.identity()));
            Map<String, String> errors = new LinkedHashMap<>();
            List<CellUpdate> updates = new ArrayList<>();
            for (Map.Entry<String, String> entry : form.getScores().entrySet()) {
                String key = entry.getKey();
                Enrollment enrollment = enrollments.get(keyPart(key, 0));
                GradeComponent component = components.get(keyPart(key, 1));
                if (enrollment == null || component == null) {
                    throw new BusinessException(GradeMessages.STUDENTS_CHANGED);
                }
                Grade existing = data.grades().get(key);
                if (existing != null && existing.isPublished()) {
                    throw new BusinessException(GradeMessages.CELL_PUBLISHED);
                }
                BigDecimal score;
                try {
                    score = GradeRule.parse(entry.getValue());
                } catch (BusinessException e) {
                    errors.put("scores[" + key + "]", e.getMessage());
                    continue;
                }
                String error = GradeRule.validate(score, component.getMaxScore());
                if (error != null) {
                    errors.put("scores[" + key + "]", error);
                    continue;
                }
                updates.add(new CellUpdate(enrollment, component, score));
            }
            CatalogSupport.throwIfAny(errors);
            List<String> changes = apply(actor, data.grades(), updates);
            if (!changes.isEmpty()) {
                auditService.log(actor.getUserId(), AuditActions.ENTER_GRADES, AuditActions.ENTITY_CLASS_SECTION,
                        sectionId, "section=" + data.section().getCode() + ", changes=" + changes, ipAddress);
            }
            return changes.size();
        });
    }

    /**
     * Công bố (trưởng bộ môn của bộ môn, ADMIN): mọi ô đã có điểm chưa công bố của sinh viên chưa rút khỏi lớp.
     * Trả số ô đã công bố.
     */
    @Transactional
    public int publish(AppUserPrincipal actor, Long sectionId, String ipAddress) {
        return guarded(() -> {
            scope.section(actor, Access.MANAGER, sectionId, false);
            ClassSection section = lock(sectionId);
            GradeRule.requireCompleteWeights(components(section));
            List<Grade> grades = gradeRepository.findPublishable(sectionId);
            if (grades.isEmpty()) {
                throw new BusinessException(GradeMessages.NOTHING_TO_PUBLISH);
            }
            User publisher = userRepository.getReferenceById(actor.getUserId());
            LocalDateTime now = AppTime.now();
            for (Grade grade : grades) {
                grade.setPublished(true);
                grade.setPublishedBy(publisher);
                grade.setPublishedAt(now);
            }
            gradeRepository.flush();
            auditService.log(actor.getUserId(), AuditActions.PUBLISH_GRADE, AuditActions.ENTITY_CLASS_SECTION,
                    sectionId, "section=" + section.getCode() + ", count=" + grades.size(), ipAddress);
            // Báo cho sinh viên có ô vừa được công bố và phụ huynh đã liên kết, trong cùng transaction.
            Set<Long> studentIds = grades.stream().map(g -> g.getEnrollment().getStudent().getId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            notificationService.sendAutomatic(actor.getUserId(), "[" + section.getCode() + "] Đã công bố điểm",
                    "Điểm môn " + section.getSubject().getCode() + " - " + section.getSubject().getName()
                            + " (lớp " + section.getCode() + ") vừa được công bố. Xem chi tiết ở mục Điểm.",
                    NotificationPriority.NORMAL, NotificationTargetType.CLASS_SECTION, sectionId,
                    audience.studentsAndParents(studentIds));
            return grades.size();
        });
    }

    @Transactional(readOnly = true)
    public GradeAdjustView adjustView(AppUserPrincipal actor, Long sectionId, Long gradeId) {
        ClassSection section = scope.section(actor, Access.MANAGER, sectionId, false);
        Grade grade = findInSection(sectionId, gradeId);
        return new GradeAdjustView(grade.getId(), section.getCode(),
                grade.getEnrollment().getStudent().getStudentCode(),
                grade.getEnrollment().getStudent().getUser().getFullName(), grade.getComponent().getName(),
                grade.getComponent().getMaxScore(), grade.getScore(), grade.getNote());
    }

    /**
     * Điều chỉnh ô ĐÃ công bố (trưởng bộ môn của bộ môn, ADMIN): điểm mới khác điểm cũ và lý do đều bắt buộc; lý do
     * ghi vào grades.note, graded_by/graded_at thành người sửa, ô vẫn ở trạng thái đã công bố.
     */
    @Transactional
    public void adjust(AppUserPrincipal actor, Long sectionId, Long gradeId, GradeAdjustForm form,
                       String ipAddress) {
        guarded(() -> {
            scope.section(actor, Access.MANAGER, sectionId, false);
            ClassSection section = lock(sectionId);
            Grade grade = findInSection(sectionId, gradeId);
            if (!grade.isPublished()) {
                throw new BusinessException(GradeMessages.NOT_PUBLISHED);
            }
            BigDecimal score = GradeRule.parse(form.getScore());
            String error = score == null ? GradeMessages.SCORE_REQUIRED
                    : GradeRule.validate(score, grade.getComponent().getMaxScore());
            if (error == null && GradeRule.sameScore(score, grade.getScore())) {
                error = GradeMessages.SCORE_UNCHANGED;
            }
            if (error != null) {
                throw new BusinessException(error, "score");
            }
            String reason = CatalogSupport.trimToNull(form.getReason());
            if (reason == null) {
                throw new BusinessException(GradeMessages.REASON_REQUIRED, "reason");
            }
            if (reason.length() > 255) {
                throw new BusinessException(GradeMessages.REASON_SIZE, "reason");
            }
            BigDecimal oldScore = grade.getScore();
            String oldNote = grade.getNote();
            grade.setScore(GradeRule.normalize(score));
            grade.setNote(reason);
            grade.setGradedBy(userRepository.getReferenceById(actor.getUserId()));
            grade.setGradedAt(AppTime.now());
            gradeRepository.flush();
            auditService.log(actor.getUserId(), AuditActions.ADJUST_GRADE, AuditActions.ENTITY_GRADE, gradeId,
                    "section=" + section.getCode() + ", student="
                            + grade.getEnrollment().getStudent().getStudentCode() + ", component="
                            + grade.getComponent().getName() + ", score=" + format(oldScore) + " -> "
                            + format(grade.getScore()) + ", oldNote=" + (oldNote == null ? "-" : oldNote)
                            + ", reason=" + reason,
                    ipAddress);
            return null;
        });
    }

    /**
     * Giảng viên của lớp; khóa lớp; lớp chưa hủy; tổng trọng số phải đúng 100%. Dùng chung cho nhập tay và nhập
     * Excel.
     */
    Loaded lockForEntry(AppUserPrincipal actor, Long sectionId) {
        scope.section(actor, Access.TEACHER, sectionId, true);
        ClassSection section = lock(sectionId);
        if (section.getStatus() == SectionStatus.CANCELLED) {
            throw new BusinessException(GradeMessages.SECTION_CANCELLED);
        }
        Loaded data = load(section);
        GradeRule.requireCompleteWeights(data.components());
        return data;
    }

    /** Bảng điểm (chỉ sinh viên đang học) để tạo file mẫu Excel. */
    Loaded loadForTeacher(AppUserPrincipal actor, Long sectionId) {
        return load(scope.section(actor, Access.TEACHER, sectionId, false));
    }

    /**
     * Ghi các ô có điểm khác điểm hiện tại: tạo dòng mới (ô chưa có dòng mà để trống thì bỏ qua), gán graded_by,
     * graded_at. Ô đã công bố không bao giờ được ghi ở đây. Trả mô tả từng thay đổi cho audit.
     */
    List<String> apply(AppUserPrincipal actor, Map<String, Grade> existing, List<CellUpdate> updates) {
        User marker = userRepository.getReferenceById(actor.getUserId());
        LocalDateTime now = AppTime.now();
        List<Grade> toSave = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        for (CellUpdate update : updates) {
            Grade grade = existing.get(GradeSheet.key(update.enrollment().getId(), update.component().getId()));
            if (grade != null && grade.isPublished()) {
                throw new BusinessException(GradeMessages.CELL_PUBLISHED);
            }
            BigDecimal old = grade == null ? null : grade.getScore();
            if (GradeRule.sameScore(old, update.score())) {
                continue;
            }
            if (grade == null) {
                grade = new Grade();
                grade.setEnrollment(update.enrollment());
                grade.setComponent(update.component());
            }
            grade.setScore(GradeRule.normalize(update.score()));
            grade.setGradedBy(marker);
            grade.setGradedAt(now);
            toSave.add(grade);
            changes.add(update.enrollment().getStudent().getStudentCode() + "/" + update.component().getName()
                    + ": " + format(old) + " -> " + format(update.score()));
        }
        gradeRepository.saveAll(toSave);
        gradeRepository.flush();
        return changes;
    }

    /**
     * Đổi lỗi database thành thông báo: vi phạm ràng buộc qua ConstraintViolationMapper, deadlock hoặc hết thời gian
     * chờ khóa thành "vui lòng thử lại".
     */
    static <T> T guarded(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(GradeMessages.CONCURRENT_CHANGE);
        }
    }

    private Loaded load(ClassSection section) {
        Map<String, Grade> grades = gradeRepository.findBySectionId(section.getId()).stream()
                .collect(Collectors.toMap(g -> GradeSheet.key(g.getEnrollment().getId(), g.getComponent().getId()),
                        Function.identity()));
        return new Loaded(section, components(section),
                enrollmentRepository.findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(section.getId(),
                        EnrollmentStatus.ENROLLED),
                grades);
    }

    private List<GradeComponent> components(ClassSection section) {
        return gradeComponentRepository.findBySubjectIdOrderByIdAsc(section.getSubject().getId());
    }

    private ClassSection lock(Long sectionId) {
        return classSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new NotFoundException(SectionMessages.SECTION_NOT_FOUND));
    }

    private Grade findInSection(Long sectionId, Long gradeId) {
        return gradeRepository.findWithDetailsById(gradeId)
                .filter(g -> Objects.equals(g.getEnrollment().getClassSection().getId(), sectionId))
                .orElseThrow(() -> new NotFoundException(GradeMessages.GRADE_NOT_FOUND));
    }

    /** Phần thứ index của khóa "enrollmentId_componentId"; khóa sai dạng → null (coi như danh sách đã đổi). */
    private static Long keyPart(String key, int index) {
        String[] parts = key == null ? new String[0] : key.split("_");
        if (parts.length != 2) {
            return null;
        }
        try {
            return Long.valueOf(parts[index]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(BigDecimal score) {
        return score == null ? "-" : score.stripTrailingZeros().toPlainString();
    }
}
