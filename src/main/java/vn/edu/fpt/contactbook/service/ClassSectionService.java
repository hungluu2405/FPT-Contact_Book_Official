package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ClassSectionForm;
import vn.edu.fpt.contactbook.dto.ClassSectionView;
import vn.edu.fpt.contactbook.dto.SectionFilter;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lớp học phần (SRS FR-ORG-05): môn, học kỳ, giảng viên cùng bộ môn với môn, sĩ số tối đa, trạng thái.
 * ADMIN mọi lớp; HEAD_OF_DEPARTMENT chỉ lớp có môn thuộc bộ môn mình (SubjectAccessPolicy).
 * Mã lớp không có UNIQUE trong database nên tạo/sửa chạy SERIALIZABLE để hai người không lưu trùng mã.
 */
@Service
@RequiredArgsConstructor
public class ClassSectionService {

    static final int MAX_STUDENTS = Short.MAX_VALUE;
    static final int MAX_CODE_LENGTH = 50;

    private final ClassSectionRepository classSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final SemesterRepository semesterRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectService subjectService;
    private final SubjectAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ClassSectionView> list(AppUserPrincipal actor, SectionFilter filter, int page) {
        accessPolicy.requireCanManage(actor);
        boolean scoped = !accessPolicy.isAdmin(actor);
        // Danh sách IN không được rỗng nên ADMIN (không lọc phạm vi) truyền một id giả.
        Set<Long> scope = scoped ? accessPolicy.headDepartmentIds(actor) : Set.of(-1L);
        Page<ClassSection> sections = classSectionRepository.search(
                LikePatterns.containsIgnoreCase(filter.getKeyword()), filter.getSemesterId(), filter.getStatus(),
                scoped, scope, CatalogSupport.page(page));
        Map<Long, Long> enrolled = enrolledCounts(sections.map(ClassSection::getId).getContent());
        return sections.map(section -> toView(section, enrolled.getOrDefault(section.getId(), 0L), false));
    }

    @Transactional(readOnly = true)
    public ClassSectionView detail(AppUserPrincipal actor, Long id) {
        ClassSection section = findInScope(actor, id);
        return toView(section, enrollmentRepository.countByClassSectionIdAndStatus(id, EnrollmentStatus.ENROLLED),
                enrollmentRepository.existsByClassSectionId(id));
    }

    @Transactional(readOnly = true)
    public ClassSectionForm editForm(AppUserPrincipal actor, Long id) {
        ClassSection section = findInScope(actor, id);
        ClassSectionForm form = new ClassSectionForm();
        form.setSubjectId(section.getSubject().getId());
        form.setSemesterId(section.getSemester().getId());
        form.setTeacherId(section.getTeacher().getId());
        form.setCode(section.getCode());
        form.setMaxStudents(section.getMaxStudents() == null ? null : section.getMaxStudents().intValue());
        form.setStatus(section.getStatus());
        return form;
    }

    /** Mã đề xuất cho môn và học kỳ đã chọn; môn phải trong phạm vi của actor. */
    @Transactional(readOnly = true)
    public String suggestCode(AppUserPrincipal actor, Long subjectId, Long semesterId) {
        accessPolicy.requireCanManage(actor);
        if (subjectId == null || semesterId == null) {
            throw new BusinessException(SectionMessages.SUGGEST_NEEDS_SUBJECT_SEMESTER, "code");
        }
        Subject subject = subjectService.findInScope(actor, subjectId);
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new BusinessException(SectionMessages.SEMESTER_REQUIRED, "semesterId"));
        return suggestCode(subject, semester);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long create(AppUserPrincipal actor, ClassSectionForm form, String ipAddress) {
        accessPolicy.requireCanManage(actor);
        ClassSection section = new ClassSection();
        try {
            apply(actor, section, form, 0, false);
            classSectionRepository.saveAndFlush(section);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(SectionMessages.CONCURRENT_CHANGE);
        }
        auditService.log(actor.getUserId(), AuditActions.CREATE_CLASS_SECTION, AuditActions.ENTITY_CLASS_SECTION,
                section.getId(), detail(section), ipAddress);
        return section.getId();
    }

    /**
     * Khóa dòng lớp trước khi đếm sinh viên, để không ai ghi danh chen vào giữa lúc kiểm tra sĩ số mới.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void update(AppUserPrincipal actor, Long id, ClassSectionForm form, String ipAddress) {
        findInScope(actor, id);
        ClassSection section;
        try {
            section = classSectionRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException(SectionMessages.SECTION_NOT_FOUND));
            long enrolled = enrollmentRepository.countByClassSectionIdAndStatus(id, EnrollmentStatus.ENROLLED);
            apply(actor, section, form, enrolled, enrollmentRepository.existsByClassSectionId(id));
            classSectionRepository.saveAndFlush(section);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(SectionMessages.CONCURRENT_CHANGE);
        }
        auditService.log(actor.getUserId(), AuditActions.UPDATE_CLASS_SECTION, AuditActions.ENTITY_CLASS_SECTION, id,
                detail(section), ipAddress);
    }

    /**
     * Lớp trong phạm vi của actor (theo bộ môn của môn học). Với HEAD_OF_DEPARTMENT, lớp không tồn tại cũng
     * trả 403 như lớp ngoài phạm vi, để không dò được id; ADMIN nhận 404. Gọi trong transaction của service khác.
     */
    ClassSection findInScope(AppUserPrincipal actor, Long id) {
        accessPolicy.requireCanManage(actor);
        ClassSection section = classSectionRepository.findById(id).orElse(null);
        if (accessPolicy.isAdmin(actor)) {
            if (section == null) {
                throw new NotFoundException(SectionMessages.SECTION_NOT_FOUND);
            }
            return section;
        }
        if (section == null || !accessPolicy.canManageDepartment(actor, section.getSubject().getDepartment().getId())) {
            throw new AccessDeniedException("Class section outside department scope");
        }
        return section;
    }

    private void apply(AppUserPrincipal actor, ClassSection section, ClassSectionForm form, long enrolled,
                       boolean hasEnrollments) {
        Map<String, String> errors = new LinkedHashMap<>();
        // Môn ngoài phạm vi trưởng bộ môn (chỉ có thể do sửa request) → 403 qua findInScope.
        Subject subject = null;
        if (form.getSubjectId() != null && subjectRepository.existsById(form.getSubjectId())) {
            subject = subjectService.findInScope(actor, form.getSubjectId());
        }
        if (subject == null) {
            errors.put("subjectId", SectionMessages.SUBJECT_REQUIRED);
        }
        Semester semester = form.getSemesterId() == null ? null
                : semesterRepository.findById(form.getSemesterId()).orElse(null);
        if (semester == null) {
            errors.put("semesterId", SectionMessages.SEMESTER_REQUIRED);
        }
        if (section.getId() != null && hasEnrollments) {
            if (subject != null && !Objects.equals(section.getSubject().getId(), subject.getId())) {
                errors.put("subjectId", SectionMessages.SUBJECT_LOCKED);
            }
            if (semester != null && !Objects.equals(section.getSemester().getId(), semester.getId())) {
                errors.put("semesterId", SectionMessages.SEMESTER_LOCKED);
            }
        }
        Teacher teacher = form.getTeacherId() == null ? null
                : teacherRepository.findById(form.getTeacherId()).orElse(null);
        if (teacher == null) {
            errors.put("teacherId", SectionMessages.TEACHER_REQUIRED);
        } else if (subject != null
                && !Objects.equals(teacher.getDepartment().getId(), subject.getDepartment().getId())) {
            errors.put("teacherId", SectionMessages.TEACHER_OTHER_DEPARTMENT);
        }
        checkMaxStudents(form.getMaxStudents(), enrolled, errors);
        if (form.getStatus() == null) {
            errors.put("status", "Vui lòng chọn trạng thái");
        }

        String code = CatalogSupport.normalizeCode(form.getCode());
        if (code == null && subject != null && semester != null && !errors.containsKey("subjectId")
                && !errors.containsKey("semesterId")) {
            code = suggestCode(subject, semester);
        }
        if (code == null) {
            errors.putIfAbsent("code", "Vui lòng nhập mã hoặc chọn môn học và học kỳ để hệ thống đề xuất");
        } else if (code.length() > MAX_CODE_LENGTH) {
            errors.putIfAbsent("code", "Mã tối đa " + MAX_CODE_LENGTH + " ký tự");
        } else if (section.getId() == null ? classSectionRepository.existsByCode(code)
                : classSectionRepository.existsByCodeAndIdNot(code, section.getId())) {
            errors.putIfAbsent("code", SectionMessages.CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);

        section.setSubject(subject);
        section.setSemester(semester);
        section.setTeacher(teacher);
        section.setCode(code);
        section.setMaxStudents(form.getMaxStudents() == null ? null : form.getMaxStudents().shortValue());
        section.setStatus(form.getStatus());
    }

    /** ck_cs_max: NULL (không giới hạn) hoặc > 0; SMALLINT; không nhỏ hơn số sinh viên đang ENROLLED. */
    static void checkMaxStudents(Integer maxStudents, long enrolled, Map<String, String> errors) {
        if (maxStudents == null) {
            return;
        }
        if (maxStudents < 1 || maxStudents > MAX_STUDENTS) {
            errors.put("maxStudents", SectionMessages.MAX_STUDENTS_RANGE);
        } else if (maxStudents < enrolled) {
            errors.put("maxStudents", SectionMessages.MAX_BELOW_ENROLLED.formatted(enrolled));
        }
    }

    private String suggestCode(Subject subject, Semester semester) {
        String prefix = subject.getCode() + "-" + semester.getCode() + "-";
        return nextCode(prefix, classSectionRepository.findCodesLike(LikePatterns.startsWith(prefix)));
    }

    /** prefix + số thứ tự nhỏ nhất (từ 01) chưa có trong existing; so khớp không phân biệt hoa thường. */
    static String nextCode(String prefix, Collection<String> existing) {
        Set<Integer> used = new HashSet<>();
        for (String code : existing) {
            if (code.length() > prefix.length() && code.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String rest = code.substring(prefix.length());
                if (rest.matches("\\d{1,4}")) {
                    used.add(Integer.parseInt(rest));
                }
            }
        }
        int next = 1;
        while (used.contains(next)) {
            next++;
        }
        return prefix + "%02d".formatted(next);
    }

    private Map<Long, Long> enrolledCounts(List<Long> sectionIds) {
        if (sectionIds.isEmpty()) {
            return Map.of();
        }
        return enrollmentRepository.countBySections(sectionIds, EnrollmentStatus.ENROLLED).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    static ClassSectionView toView(ClassSection section, long enrolled, boolean hasEnrollments) {
        Subject subject = section.getSubject();
        Semester semester = section.getSemester();
        Teacher teacher = section.getTeacher();
        return new ClassSectionView(section.getId(), section.getCode(), subject.getId(), subject.getCode(),
                subject.getName(), subject.getDepartment().getId(), subject.getDepartment().getCode(), semester.getId(),
                semester.getCode(), semester.getName(), teacher.getId(), teacher.getTeacherCode(),
                teacher.getUser().getFullName(), section.getMaxStudents(), section.getStatus(), enrolled,
                hasEnrollments);
    }

    private static String detail(ClassSection section) {
        return "code=" + section.getCode() + ", subject=" + section.getSubject().getCode() + ", semester="
                + section.getSemester().getCode() + ", status=" + section.getStatus();
    }
}
