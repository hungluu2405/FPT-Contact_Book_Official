package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.FacultyForm;
import vn.edu.fpt.contactbook.dto.FacultyView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.repository.FacultyRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh mục khoa (SRS FR-ORG-01): thêm, sửa, xem. Chỉ ADMIN. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<FacultyView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return facultyRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), CatalogSupport.page(page))
                .map(FacultyService::toView);
    }

    @Transactional(readOnly = true)
    public FacultyView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public FacultyForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        Faculty faculty = find(id);
        FacultyForm form = new FacultyForm();
        form.setCode(faculty.getCode());
        form.setName(faculty.getName());
        form.setDescription(faculty.getDescription());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, FacultyForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Faculty faculty = apply(new Faculty(), form);
        save(faculty);
        auditService.log(actor.getUserId(), AuditActions.CREATE_FACULTY, AuditActions.ENTITY_FACULTY,
                faculty.getId(), "code=" + faculty.getCode(), ipAddress);
        return faculty.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, FacultyForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Faculty faculty = apply(find(id), form);
        save(faculty);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_FACULTY, AuditActions.ENTITY_FACULTY, id,
                "code=" + faculty.getCode(), ipAddress);
    }

    private Faculty apply(Faculty faculty, FacultyForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 150, "name", "Vui lòng nhập tên",
                "Tên tối đa 150 ký tự", errors);
        String description = CatalogSupport.optionalText(form.getDescription(), 500, "description",
                "Mô tả tối đa 500 ký tự", errors);
        if (code != null && (faculty.getId() == null ? facultyRepository.existsByCode(code)
                : facultyRepository.existsByCodeAndIdNot(code, faculty.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.FACULTY_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        faculty.setCode(code);
        faculty.setName(name);
        faculty.setDescription(description);
        return faculty;
    }

    private void save(Faculty faculty) {
        try {
            facultyRepository.saveAndFlush(faculty);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Faculty find(Long id) {
        return facultyRepository.findById(id).orElseThrow(() -> new NotFoundException(CatalogMessages.FACULTY_NOT_FOUND));
    }

    private static FacultyView toView(Faculty faculty) {
        return new FacultyView(faculty.getId(), faculty.getCode(), faculty.getName(), faculty.getDescription());
    }
}
