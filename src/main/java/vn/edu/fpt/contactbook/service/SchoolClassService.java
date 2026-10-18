package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.SchoolClassForm;
import vn.edu.fpt.contactbook.dto.SchoolClassView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Danh mục lớp hành chính (SRS FR-ORG-02): ngành, mã, tên, niên khóa, giảng viên chủ nhiệm tùy chọn.
 * Chỉ ADMIN. Lớp đã có sinh viên thì không đổi được ngành. Không có chức năng xóa.
 */
@Service
@RequiredArgsConstructor
public class SchoolClassService {

    static final int MIN_INTAKE_YEAR = 2000;
    static final int MAX_INTAKE_YEAR = 2100;

    private final SchoolClassRepository schoolClassRepository;
    private final MajorRepository majorRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<SchoolClassView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return schoolClassRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getGroupId(),
                CatalogSupport.page(page)).map(SchoolClassService::toView);
    }

    @Transactional(readOnly = true)
    public SchoolClassView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public SchoolClassForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        SchoolClass schoolClass = find(id);
        SchoolClassForm form = new SchoolClassForm();
        form.setMajorId(schoolClass.getMajor().getId());
        form.setCode(schoolClass.getCode());
        form.setName(schoolClass.getName());
        form.setIntakeYear(schoolClass.getIntakeYear().intValue());
        Teacher homeroom = schoolClass.getHomeroomTeacher();
        form.setHomeroomTeacherCode(homeroom == null ? null : homeroom.getTeacherCode());
        return form;
    }

    @Transactional
    public Long create(AppUserPrincipal actor, SchoolClassForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        SchoolClass schoolClass = apply(new SchoolClass(), form);
        save(schoolClass);
        auditService.log(actor.getUserId(), AuditActions.CREATE_CLASS, AuditActions.ENTITY_CLASS,
                schoolClass.getId(), "code=" + schoolClass.getCode(), ipAddress);
        return schoolClass.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, SchoolClassForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        SchoolClass schoolClass = apply(find(id), form);
        save(schoolClass);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_CLASS, AuditActions.ENTITY_CLASS, id,
                "code=" + schoolClass.getCode(), ipAddress);
    }

    private SchoolClass apply(SchoolClass schoolClass, SchoolClassForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        Major major = form.getMajorId() == null ? null : majorRepository.findById(form.getMajorId()).orElse(null);
        if (major == null) {
            errors.put("majorId", CatalogMessages.MAJOR_REQUIRED);
        } else if (schoolClass.getId() != null && !Objects.equals(schoolClass.getMajor().getId(), major.getId())
                && studentRepository.existsBySchoolClassId(schoolClass.getId())) {
            errors.put("majorId", CatalogMessages.CLASS_MAJOR_LOCKED);
        }
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 150, "name", "Vui lòng nhập tên",
                "Tên tối đa 150 ký tự", errors);
        Integer intakeYear = form.getIntakeYear();
        if (intakeYear == null) {
            errors.put("intakeYear", "Vui lòng nhập niên khóa");
        } else if (intakeYear < MIN_INTAKE_YEAR || intakeYear > MAX_INTAKE_YEAR) {
            errors.put("intakeYear", "Niên khóa phải từ " + MIN_INTAKE_YEAR + " đến " + MAX_INTAKE_YEAR);
        }
        // Mã giảng viên được lưu viết hoa (ASSUMPTIONS 24) nên chuẩn hóa như mã danh mục trước khi tra.
        String teacherCode = CatalogSupport.normalizeCode(form.getHomeroomTeacherCode());
        Teacher homeroom = teacherCode == null ? null : teacherRepository.findByTeacherCode(teacherCode).orElse(null);
        if (teacherCode != null && homeroom == null) {
            errors.put("homeroomTeacherCode", CatalogMessages.HOMEROOM_NOT_FOUND);
        }
        if (code != null && (schoolClass.getId() == null ? schoolClassRepository.existsByCode(code)
                : schoolClassRepository.existsByCodeAndIdNot(code, schoolClass.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.CLASS_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        schoolClass.setMajor(major);
        schoolClass.setCode(code);
        schoolClass.setName(name);
        schoolClass.setIntakeYear(intakeYear.shortValue());
        schoolClass.setHomeroomTeacher(homeroom);
        return schoolClass;
    }

    private void save(SchoolClass schoolClass) {
        try {
            schoolClassRepository.saveAndFlush(schoolClass);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private SchoolClass find(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CatalogMessages.CLASS_NOT_FOUND));
    }

    private static SchoolClassView toView(SchoolClass schoolClass) {
        Major major = schoolClass.getMajor();
        Teacher homeroom = schoolClass.getHomeroomTeacher();
        return new SchoolClassView(schoolClass.getId(), schoolClass.getCode(), schoolClass.getName(),
                schoolClass.getIntakeYear(), major.getId(), major.getCode(), major.getName(),
                homeroom == null ? null : homeroom.getTeacherCode(),
                homeroom == null ? null : homeroom.getUser().getFullName());
    }
}
