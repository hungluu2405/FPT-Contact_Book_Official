package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.GradeComponentForm;
import vn.edu.fpt.contactbook.dto.GradeComponentSummary;
import vn.edu.fpt.contactbook.dto.GradeComponentView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Đầu điểm của môn học: thêm, sửa, xóa (khi chưa có điểm). Phân quyền như môn học (SubjectService.findInScope).
 * Tổng trọng số khác 100% chỉ là cảnh báo ở giai đoạn này (ASSUMPTIONS).
 */
@Service
@RequiredArgsConstructor
public class GradeComponentService {

    static final BigDecimal HUNDRED = new BigDecimal("100");
    /** weight DECIMAL(5,2), max_score DECIMAL(4,2): tối đa 2 chữ số thập phân. */
    static final int SCALE = 2;

    private final SubjectService subjectService;
    private final GradeComponentRepository gradeComponentRepository;
    private final GradeRepository gradeRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public GradeComponentSummary summary(AppUserPrincipal actor, Long subjectId) {
        subjectService.findInScope(actor, subjectId);
        Set<Long> withGrades = gradeRepository.findComponentIdsWithGrades(subjectId);
        List<GradeComponentView> components = gradeComponentRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                .map(component -> new GradeComponentView(component.getId(), component.getName(),
                        component.getWeight(), component.getMaxScore(), withGrades.contains(component.getId())))
                .toList();
        return summarize(components);
    }

    /** Tổng trọng số; complete chỉ khi đúng 100 (so sánh giá trị, 100.00 = 100). */
    static GradeComponentSummary summarize(List<GradeComponentView> components) {
        BigDecimal total = components.stream().map(GradeComponentView::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GradeComponentSummary(components, total, total.compareTo(HUNDRED) == 0);
    }

    @Transactional(readOnly = true)
    public GradeComponentForm editForm(AppUserPrincipal actor, Long subjectId, Long componentId) {
        GradeComponent component = findInSubject(actor, subjectId, componentId);
        GradeComponentForm form = new GradeComponentForm();
        form.setName(component.getName());
        form.setWeight(component.getWeight());
        form.setMaxScore(component.getMaxScore());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, Long subjectId, GradeComponentForm form, String ipAddress) {
        Subject subject = subjectService.findInScope(actor, subjectId);
        GradeComponent component = new GradeComponent();
        component.setSubject(subject);
        apply(component, subjectId, form);
        save(component);
        auditService.log(actor.getUserId(), AuditActions.CREATE_GRADE_COMPONENT, AuditActions.ENTITY_GRADE_COMPONENT,
                component.getId(), detail(subject, component), ipAddress);
        return component.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long subjectId, Long componentId, GradeComponentForm form,
                       String ipAddress) {
        GradeComponent component = findInSubject(actor, subjectId, componentId);
        apply(component, subjectId, form);
        save(component);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_GRADE_COMPONENT, AuditActions.ENTITY_GRADE_COMPONENT,
                componentId, detail(component.getSubject(), component), ipAddress);
    }

    @Transactional
    public void delete(AppUserPrincipal actor, Long subjectId, Long componentId, String ipAddress) {
        GradeComponent component = findInSubject(actor, subjectId, componentId);
        if (gradeRepository.existsByComponentId(componentId)) {
            throw new BusinessException(CatalogMessages.COMPONENT_HAS_GRADES);
        }
        String detail = detail(component.getSubject(), component);
        try {
            gradeComponentRepository.delete(component);
            gradeComponentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Điểm được nhập chen vào giữa lúc kiểm tra và lúc xóa: fk_grades_component chặn lại.
            throw ConstraintViolationMapper.map(e);
        }
        auditService.log(actor.getUserId(), AuditActions.DELETE_GRADE_COMPONENT, AuditActions.ENTITY_GRADE_COMPONENT,
                componentId, detail, ipAddress);
    }

    private void apply(GradeComponent component, Long subjectId, GradeComponentForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = CatalogSupport.requireText(form.getName(), 100, "name", "Vui lòng nhập tên đầu điểm",
                "Tên tối đa 100 ký tự", errors);
        checkWeight(form.getWeight(), errors);
        checkMaxScore(form.getMaxScore(), errors);
        if (name != null && (component.getId() == null
                ? gradeComponentRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, name)
                : gradeComponentRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(subjectId, name,
                component.getId()))) {
            errors.putIfAbsent("name", CatalogMessages.COMPONENT_NAME_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        component.setName(name);
        component.setWeight(form.getWeight());
        component.setMaxScore(form.getMaxScore());
    }

    /** ck_gc_weight: 0 < weight <= 100, tối đa 2 chữ số thập phân. */
    static void checkWeight(BigDecimal weight, Map<String, String> errors) {
        if (weight == null) {
            errors.putIfAbsent("weight", "Vui lòng nhập trọng số");
        } else if (weight.signum() <= 0 || weight.compareTo(HUNDRED) > 0) {
            errors.putIfAbsent("weight", CatalogMessages.WEIGHT_RANGE);
        } else if (weight.stripTrailingZeros().scale() > SCALE) {
            errors.putIfAbsent("weight", "Trọng số tối đa 2 chữ số thập phân");
        }
    }

    /** 0 < max_score <= 10 (điểm trong grades tối đa 10 theo ck_grades_score), tối đa 2 chữ số thập phân. */
    static void checkMaxScore(BigDecimal maxScore, Map<String, String> errors) {
        if (maxScore == null) {
            errors.putIfAbsent("maxScore", "Vui lòng nhập điểm tối đa");
        } else if (maxScore.signum() <= 0 || maxScore.compareTo(BigDecimal.TEN) > 0) {
            errors.putIfAbsent("maxScore", "Điểm tối đa phải lớn hơn 0 và không quá 10");
        } else if (maxScore.stripTrailingZeros().scale() > SCALE) {
            errors.putIfAbsent("maxScore", "Điểm tối đa tối đa 2 chữ số thập phân");
        }
    }

    /** Đầu điểm phải thuộc đúng môn trên URL; kiểm tra phạm vi môn trước để HEAD không dò được id. */
    private GradeComponent findInSubject(AppUserPrincipal actor, Long subjectId, Long componentId) {
        subjectService.findInScope(actor, subjectId);
        return gradeComponentRepository.findById(componentId)
                .filter(component -> Objects.equals(component.getSubject().getId(), subjectId))
                .orElseThrow(() -> new NotFoundException(CatalogMessages.COMPONENT_NOT_FOUND));
    }

    private void save(GradeComponent component) {
        try {
            gradeComponentRepository.saveAndFlush(component);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private static String detail(Subject subject, GradeComponent component) {
        return "subject=" + subject.getCode() + ", name=" + component.getName() + ", weight="
                + component.getWeight().toPlainString();
    }
}
