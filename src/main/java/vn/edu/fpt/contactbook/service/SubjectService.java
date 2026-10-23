package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.SubjectForm;
import vn.edu.fpt.contactbook.dto.SubjectView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Danh mục môn học (SRS FR-ORG-03). ADMIN mọi môn; HEAD_OF_DEPARTMENT chỉ môn thuộc bộ môn mình
 * (SubjectAccessPolicy). Môn đã có lớp học phần thì không đổi được bộ môn. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class SubjectService {

    static final int MIN_CREDITS = 1;
    static final int MAX_CREDITS = 20;

    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;
    private final ClassSectionRepository classSectionRepository;
    private final SubjectAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<SubjectView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireCanManage(actor);
        boolean scoped = !accessPolicy.isAdmin(actor);
        // ADMIN không lọc phạm vi; vẫn phải truyền một danh sách IN không rỗng.
        Collection<Long> scope = scoped ? accessPolicy.headDepartmentIds(actor) : CatalogSupport.NO_IDS;
        return subjectRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getGroupId(),
                scoped, scope, CatalogSupport.page(page)).map(SubjectService::toView);
    }

    @Transactional(readOnly = true)
    public SubjectView detail(AppUserPrincipal actor, Long id) {
        return toView(findInScope(actor, id));
    }

    @Transactional(readOnly = true)
    public SubjectForm editForm(AppUserPrincipal actor, Long id) {
        Subject subject = findInScope(actor, id);
        SubjectForm form = new SubjectForm();
        form.setDepartmentId(subject.getDepartment().getId());
        form.setCode(subject.getCode());
        form.setName(subject.getName());
        form.setCredits(subject.getCredits().intValue());
        form.setDescription(subject.getDescription());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, SubjectForm form, String ipAddress) {
        accessPolicy.requireCanManage(actor);
        Subject subject = apply(actor, new Subject(), form);
        save(subject);
        auditService.log(actor.getUserId(), AuditActions.CREATE_SUBJECT, AuditActions.ENTITY_SUBJECT,
                subject.getId(), "code=" + subject.getCode(), ipAddress);
        return subject.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, SubjectForm form, String ipAddress) {
        Subject subject = apply(actor, findInScope(actor, id), form);
        save(subject);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_SUBJECT, AuditActions.ENTITY_SUBJECT, id,
                "code=" + subject.getCode(), ipAddress);
    }

    /**
     * Môn trong phạm vi của actor. Với HEAD_OF_DEPARTMENT, môn không tồn tại cũng trả 403 như môn ngoài phạm vi,
     * để không dò được id; ADMIN nhận 404. Gọi trong transaction của service khác (GradeComponentService).
     */
    Subject findInScope(AppUserPrincipal actor, Long id) {
        accessPolicy.requireCanManage(actor);
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (accessPolicy.isAdmin(actor)) {
            if (subject == null) {
                throw new NotFoundException(CatalogMessages.SUBJECT_NOT_FOUND);
            }
            return subject;
        }
        if (subject == null || !accessPolicy.canManageDepartment(actor, subject.getDepartment().getId())) {
            throw new AccessDeniedException("Subject outside department scope");
        }
        return subject;
    }

    private Subject apply(AppUserPrincipal actor, Subject subject, SubjectForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        Department department = form.getDepartmentId() == null ? null
                : departmentRepository.findById(form.getDepartmentId()).orElse(null);
        if (department == null) {
            errors.put("departmentId", CatalogMessages.DEPARTMENT_REQUIRED);
        } else {
            // Ô chọn của trưởng bộ môn chỉ có bộ môn mình; gửi bộ môn khác là sửa request → 403.
            accessPolicy.requireDepartment(actor, department.getId());
            if (subject.getId() != null && !Objects.equals(subject.getDepartment().getId(), department.getId())
                    && classSectionRepository.existsBySubjectId(subject.getId())) {
                errors.put("departmentId", CatalogMessages.SUBJECT_DEPARTMENT_LOCKED);
            }
        }
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 200, "name", "Vui lòng nhập tên",
                "Tên tối đa 200 ký tự", errors);
        Integer credits = form.getCredits();
        if (credits == null) {
            errors.put("credits", "Vui lòng nhập số tiết/tuần");
        } else if (credits < MIN_CREDITS || credits > MAX_CREDITS) {
            errors.put("credits", CatalogMessages.CREDITS_RANGE);
        }
        String description = CatalogSupport.optionalText(form.getDescription(), 500, "description",
                "Mô tả tối đa 500 ký tự", errors);
        if (code != null && (subject.getId() == null ? subjectRepository.existsByCode(code)
                : subjectRepository.existsByCodeAndIdNot(code, subject.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.SUBJECT_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        subject.setDepartment(department);
        subject.setCode(code);
        subject.setName(name);
        subject.setCredits(credits.shortValue());
        subject.setDescription(description);
        return subject;
    }

    private void save(Subject subject) {
        try {
            subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    static SubjectView toView(Subject subject) {
        Department department = subject.getDepartment();
        return new SubjectView(subject.getId(), subject.getCode(), subject.getName(), subject.getCredits(),
                subject.getDescription(), department.getId(), department.getCode(), department.getName());
    }
}
