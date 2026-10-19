package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.dto.ReportSelection;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phạm vi của báo cáo điểm danh và báo cáo điểm (SRS FR-REPORT-01, 02; CLAUDE.md mục 5), là hợp của mọi vai trò:
 * ADMIN mọi lớp học phần; TEACHER lớp có teacher_id là hồ sơ giảng viên của mình; HEAD_OF_DEPARTMENT lớp có môn
 * thuộc bộ môn gắn với vai trò HEAD. Vai trò khác → 403. Tham số lọc kiểm tra lại ở đây (kể cả khi sửa tay URL):
 * lớp hoặc môn ngoài phạm vi, hoặc không tồn tại, → 403 với người không phải ADMIN để không dò được id; ADMIN
 * nhận 404 khi id không tồn tại (như giả định 40).
 */
@Component
@RequiredArgsConstructor
public class ReportScope {

    private final UserAccessPolicy userAccessPolicy;
    private final TeacherRepository teacherRepository;
    private final ClassSectionRepository classSectionRepository;
    private final SubjectRepository subjectRepository;
    private final ScheduleViewService scheduleViewService;

    /** all: ADMIN; teacherId: hồ sơ giảng viên (null nếu không có); departmentIds: bộ môn của vai trò HEAD. */
    record Scope(boolean all, Long teacherId, Set<Long> departmentIds) {

        boolean isEmpty() {
            return !all && teacherId == null && departmentIds.isEmpty();
        }

        boolean contains(ClassSection section) {
            return all || Objects.equals(section.getTeacher().getId(), teacherId)
                    || departmentIds.contains(section.getSubject().getDepartment().getId());
        }
    }

    /**
     * Bộ lọc đã kiểm tra phạm vi: section, subject null là không chọn; sections là các lớp của báo cáo (một lớp, các
     * lớp của môn, hoặc mọi lớp trong phạm vi của học kỳ), sắp theo mã môn rồi mã lớp.
     */
    record Context(Semester semester, Subject subject, ClassSection section, List<ClassSection> sections,
                   ReportSelection selection) {
    }

    Context resolve(AppUserPrincipal actor, ReportFilter filter) {
        Scope scope = of(actor);
        if (filter.sectionId() != null) {
            ClassSection section = section(scope, filter.sectionId());
            List<ClassSection> semesterSections = sections(scope, section.getSemester().getId());
            return new Context(section.getSemester(), section.getSubject(), section, List.of(section),
                    selection(section.getSemester(), section.getSubject(), section, semesterSections));
        }
        Semester semester = scheduleViewService.resolveSemester(filter.semesterId());
        Subject subject = filter.subjectId() == null ? null : subject(scope, filter.subjectId());
        List<ClassSection> semesterSections = sections(scope, semester.getId());
        List<ClassSection> sections = subject == null ? semesterSections : semesterSections.stream()
                .filter(s -> s.getSubject().getId().equals(subject.getId())).toList();
        return new Context(semester, subject, null, sections, selection(semester, subject, null, semesterSections));
    }

    Scope of(AppUserPrincipal actor) {
        if (userAccessPolicy.isAdmin(actor)) {
            return new Scope(true, null, Set.of());
        }
        if (!actor.hasRole(RoleCodes.TEACHER) && !actor.hasRole(RoleCodes.HEAD_OF_DEPARTMENT)) {
            throw new AccessDeniedException("Reports are for teachers, heads of department and admins");
        }
        Long teacherId = teacherRepository.findByUserId(actor.getUserId()).map(Teacher::getId).orElse(null);
        return new Scope(false, teacherId, userAccessPolicy.headDepartmentIds(actor));
    }

    private ClassSection section(Scope scope, Long id) {
        ClassSection section = classSectionRepository.findById(id).orElse(null);
        if (scope.all()) {
            if (section == null) {
                throw new NotFoundException(SectionMessages.SECTION_NOT_FOUND);
            }
            return section;
        }
        if (section == null || !scope.contains(section)) {
            throw new AccessDeniedException("Class section outside report scope");
        }
        return section;
    }

    /** Môn trong phạm vi: ADMIN mọi môn; HEAD môn thuộc bộ môn mình; TEACHER môn mình có dạy ở bất kỳ học kỳ nào. */
    private Subject subject(Scope scope, Long id) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (scope.all()) {
            if (subject == null) {
                throw new NotFoundException(CatalogMessages.SUBJECT_NOT_FOUND);
            }
            return subject;
        }
        boolean inScope = subject != null && (scope.departmentIds().contains(subject.getDepartment().getId())
                || (scope.teacherId() != null
                && classSectionRepository.existsBySubjectIdAndTeacherId(id, scope.teacherId())));
        if (!inScope) {
            throw new AccessDeniedException("Subject outside report scope");
        }
        return subject;
    }

    private List<ClassSection> sections(Scope scope, Long semesterId) {
        if (scope.isEmpty()) {
            return List.of();
        }
        // Danh sách IN không được rỗng (như ClassSectionService.list).
        Set<Long> departments = scope.departmentIds();
        return classSectionRepository.findForReport(semesterId, scope.all(), scope.teacherId(),
                departments.isEmpty() ? CatalogSupport.NO_IDS : departments);
    }

    private static ReportSelection selection(Semester semester, Subject subject, ClassSection section,
                                             List<ClassSection> semesterSections) {
        Map<Long, OptionView> subjects = new LinkedHashMap<>();
        for (ClassSection s : semesterSections) {
            subjects.putIfAbsent(s.getSubject().getId(), toOption(s.getSubject()));
        }
        // Môn đã chọn nhưng không có lớp ở học kỳ này vẫn hiện trong ô chọn.
        if (subject != null) {
            subjects.putIfAbsent(subject.getId(), toOption(subject));
        }
        List<OptionView> subjectOptions = subjects.values().stream()
                .sorted(Comparator.comparing(OptionView::code)).toList();
        List<OptionView> sectionOptions = semesterSections.stream()
                .filter(s -> subject == null || s.getSubject().getId().equals(subject.getId()))
                .map(s -> new OptionView(s.getId(), s.getCode(), s.getSubject().getName(), s.getSubject().getId()))
                .toList();
        return new ReportSelection(ScheduleViewService.toView(semester), subject == null ? null : subject.getId(),
                section == null ? null : section.getId(), subjectOptions, sectionOptions);
    }

    private static OptionView toOption(Subject subject) {
        return new OptionView(subject.getId(), subject.getCode(), subject.getName(), null);
    }
}
