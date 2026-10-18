package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.DepartmentForm;
import vn.edu.fpt.contactbook.dto.DepartmentView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.FacultyRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh mục bộ môn, mỗi bộ môn thuộc một khoa (SRS FR-ORG-01). Chỉ ADMIN. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final FacultyRepository facultyRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<DepartmentView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return departmentRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getGroupId(),
                CatalogSupport.page(page)).map(DepartmentService::toView);
    }

    @Transactional(readOnly = true)
    public DepartmentView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public DepartmentForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        Department department = find(id);
        DepartmentForm form = new DepartmentForm();
        form.setFacultyId(department.getFaculty().getId());
        form.setCode(department.getCode());
        form.setName(department.getName());
        form.setDescription(department.getDescription());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, DepartmentForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Department department = apply(new Department(), form);
        save(department);
        auditService.log(actor.getUserId(), AuditActions.CREATE_DEPARTMENT, AuditActions.ENTITY_DEPARTMENT,
                department.getId(), "code=" + department.getCode(), ipAddress);
        return department.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, DepartmentForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Department department = apply(find(id), form);
        save(department);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_DEPARTMENT, AuditActions.ENTITY_DEPARTMENT, id,
                "code=" + department.getCode(), ipAddress);
    }

    private Department apply(Department department, DepartmentForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        Faculty faculty = form.getFacultyId() == null ? null
                : facultyRepository.findById(form.getFacultyId()).orElse(null);
        if (faculty == null) {
            errors.put("facultyId", CatalogMessages.FACULTY_REQUIRED);
        }
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 150, "name", "Vui lòng nhập tên",
                "Tên tối đa 150 ký tự", errors);
        String description = CatalogSupport.optionalText(form.getDescription(), 500, "description",
                "Mô tả tối đa 500 ký tự", errors);
        if (code != null && (department.getId() == null ? departmentRepository.existsByCode(code)
                : departmentRepository.existsByCodeAndIdNot(code, department.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.DEPARTMENT_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        department.setFaculty(faculty);
        department.setCode(code);
        department.setName(name);
        department.setDescription(description);
        return department;
    }

    private void save(Department department) {
        try {
            departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Department find(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CatalogMessages.DEPARTMENT_NOT_FOUND));
    }

    private static DepartmentView toView(Department department) {
        Faculty faculty = department.getFaculty();
        return new DepartmentView(department.getId(), department.getCode(), department.getName(),
                department.getDescription(), faculty.getId(), faculty.getCode(), faculty.getName());
    }
}
