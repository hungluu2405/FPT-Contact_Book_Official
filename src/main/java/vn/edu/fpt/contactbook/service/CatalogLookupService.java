package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.FacultyRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;

import java.util.Collection;
import java.util.List;

/**
 * Danh mục nhỏ cho các ô chọn trên form (khoa, bộ môn, ngành, lớp, học kỳ, môn học, giảng viên).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogLookupService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;

    public List<OptionView> faculties() {
        return facultyRepository.findAllByOrderByCodeAsc().stream()
                .map(f -> new OptionView(f.getId(), f.getCode(), f.getName(), null))
                .toList();
    }

    public List<OptionView> departments() {
        return departmentRepository.findAllByOrderByCodeAsc().stream()
                .map(d -> new OptionView(d.getId(), d.getCode(), d.getName(), null))
                .toList();
    }

    public List<OptionView> departments(Collection<Long> ids) {
        return departments().stream().filter(option -> ids.contains(option.id())).toList();
    }

    public List<OptionView> majors() {
        return majorRepository.findAllByOrderByCodeAsc().stream()
                .map(m -> new OptionView(m.getId(), m.getCode(), m.getName(), null))
                .toList();
    }

    public List<OptionView> classes() {
        return schoolClassRepository.findAllByOrderByCodeAsc().stream()
                .map(c -> new OptionView(c.getId(), c.getCode(), c.getName(), c.getMajor().getId()))
                .toList();
    }

    /** Học kỳ mới nhất trước. */
    public List<OptionView> semesters() {
        return semesterRepository.findAllByOrderByStartDateDesc().stream()
                .map(s -> new OptionView(s.getId(), s.getCode(), s.getName(), null))
                .toList();
    }

    /** Môn học: groupId là bộ môn quản lý môn. */
    public List<OptionView> subjects() {
        return subjectRepository.findAllByOrderByCodeAsc().stream()
                .map(s -> new OptionView(s.getId(), s.getCode(), s.getName(), s.getDepartment().getId()))
                .toList();
    }

    /** Giảng viên: name là họ tên, groupId là bộ môn (để lọc theo bộ môn của môn học). */
    public List<OptionView> teachers() {
        return teacherRepository.findAllByOrderByTeacherCodeAsc().stream()
                .map(t -> new OptionView(t.getId(), t.getTeacherCode(), t.getUser().getFullName(),
                        t.getDepartment().getId()))
                .toList();
    }
}
