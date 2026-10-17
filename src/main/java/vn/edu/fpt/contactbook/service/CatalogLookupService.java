package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.OptionView;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;

import java.util.Collection;
import java.util.List;

/**
 * Danh mục nhỏ cho các ô chọn trên form (bộ môn, ngành, lớp).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogLookupService {

    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final SchoolClassRepository schoolClassRepository;

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
}
