package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.MajorForm;
import vn.edu.fpt.contactbook.dto.MajorView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.repository.FacultyRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Danh mục ngành, mỗi ngành thuộc một khoa (SRS FR-ORG-01). Chỉ ADMIN. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class MajorService {

    private final MajorRepository majorRepository;
    private final FacultyRepository facultyRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<MajorView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return majorRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getGroupId(),
                CatalogSupport.page(page)).map(MajorService::toView);
    }

    @Transactional(readOnly = true)
    public MajorView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public MajorForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        Major major = find(id);
        MajorForm form = new MajorForm();
        form.setFacultyId(major.getFaculty().getId());
        form.setCode(major.getCode());
        form.setName(major.getName());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, MajorForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Major major = apply(new Major(), form);
        save(major);
        auditService.log(actor.getUserId(), AuditActions.CREATE_MAJOR, AuditActions.ENTITY_MAJOR, major.getId(),
                "code=" + major.getCode(), ipAddress);
        return major.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, MajorForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Major major = apply(find(id), form);
        save(major);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_MAJOR, AuditActions.ENTITY_MAJOR, id,
                "code=" + major.getCode(), ipAddress);
    }

    private Major apply(Major major, MajorForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        Faculty faculty = form.getFacultyId() == null ? null
                : facultyRepository.findById(form.getFacultyId()).orElse(null);
        if (faculty == null) {
            errors.put("facultyId", CatalogMessages.FACULTY_REQUIRED);
        }
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 150, "name", "Vui lòng nhập tên",
                "Tên tối đa 150 ký tự", errors);
        if (code != null && (major.getId() == null ? majorRepository.existsByCode(code)
                : majorRepository.existsByCodeAndIdNot(code, major.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.MAJOR_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        major.setFaculty(faculty);
        major.setCode(code);
        major.setName(name);
        return major;
    }

    private void save(Major major) {
        try {
            majorRepository.saveAndFlush(major);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Major find(Long id) {
        return majorRepository.findById(id).orElseThrow(() -> new NotFoundException(CatalogMessages.MAJOR_NOT_FOUND));
    }

    private static MajorView toView(Major major) {
        Faculty faculty = major.getFaculty();
        return new MajorView(major.getId(), major.getCode(), major.getName(), faculty.getId(), faculty.getCode(),
                faculty.getName());
    }
}
