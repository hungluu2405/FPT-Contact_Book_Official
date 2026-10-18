package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CatalogFilter;
import vn.edu.fpt.contactbook.dto.SemesterForm;
import vn.edu.fpt.contactbook.dto.SemesterView;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Danh mục học kỳ (SRS FR-ORG-04): thêm, sửa, xem, đặt làm học kỳ hiện tại. Chỉ ADMIN. Không có chức năng xóa.
 * Index ux_semesters_current chỉ cho một dòng is_current = 1, nên cờ chỉ đổi qua setCurrent.
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final UserAccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<SemesterView> list(AppUserPrincipal actor, CatalogFilter filter, int page) {
        accessPolicy.requireAdmin(actor);
        return semesterRepository.search(LikePatterns.containsIgnoreCase(filter.getKeyword()),
                CatalogSupport.page(page)).map(SemesterService::toView);
    }

    @Transactional(readOnly = true)
    public SemesterView detail(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        return toView(find(id));
    }

    @Transactional(readOnly = true)
    public SemesterForm editForm(AppUserPrincipal actor, Long id) {
        accessPolicy.requireAdmin(actor);
        Semester semester = find(id);
        SemesterForm form = new SemesterForm();
        form.setCode(semester.getCode());
        form.setName(semester.getName());
        form.setStartDate(semester.getStartDate());
        form.setEndDate(semester.getEndDate());
        return form;
    }

    /** Học kỳ mới luôn chưa là học kỳ hiện tại (DEFAULT df_semesters_current = 0). */
    @Transactional
    public Long create(AppUserPrincipal actor, SemesterForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Semester semester = apply(new Semester(), form);
        save(semester);
        auditService.log(actor.getUserId(), AuditActions.CREATE_SEMESTER, AuditActions.ENTITY_SEMESTER,
                semester.getId(), "code=" + semester.getCode(), ipAddress);
        return semester.getId();
    }

    @Transactional
    public void update(AppUserPrincipal actor, Long id, SemesterForm form, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Semester semester = apply(find(id), form);
        save(semester);
        auditService.log(actor.getUserId(), AuditActions.UPDATE_SEMESTER, AuditActions.ENTITY_SEMESTER, id,
                "code=" + semester.getCode(), ipAddress);
    }

    /**
     * Đặt học kỳ id làm học kỳ hiện tại trong một transaction: gỡ cờ học kỳ cũ, flush để lệnh UPDATE đó tới
     * database trước, rồi mới đặt cờ học kỳ mới (nếu không, ux_semesters_current có thể thấy hai dòng = 1).
     * Hai người đặt cùng lúc: người sau vi phạm index và nhận thông báo thử lại. Đã là hiện tại thì không làm gì.
     */
    @Transactional
    public void setCurrent(AppUserPrincipal actor, Long id, String ipAddress) {
        accessPolicy.requireAdmin(actor);
        Semester target = find(id);
        if (target.isCurrent()) {
            return;
        }
        Optional<Semester> previous = semesterRepository.findByCurrentTrue();
        try {
            previous.ifPresent(old -> {
                old.setCurrent(false);
                semesterRepository.flush();
            });
            target.setCurrent(true);
            semesterRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
        auditService.log(actor.getUserId(), AuditActions.SET_CURRENT_SEMESTER, AuditActions.ENTITY_SEMESTER, id,
                "code=" + target.getCode() + previous.map(old -> ", previous=" + old.getCode()).orElse(""),
                ipAddress);
    }

    private Semester apply(Semester semester, SemesterForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        String code = CatalogSupport.requireCode(form.getCode(), errors);
        String name = CatalogSupport.requireText(form.getName(), 100, "name", "Vui lòng nhập tên",
                "Tên tối đa 100 ký tự", errors);
        checkDates(form.getStartDate(), form.getEndDate(), errors);
        if (code != null && (semester.getId() == null ? semesterRepository.existsByCode(code)
                : semesterRepository.existsByCodeAndIdNot(code, semester.getId()))) {
            errors.putIfAbsent("code", CatalogMessages.SEMESTER_CODE_TAKEN);
        }
        CatalogSupport.throwIfAny(errors);
        semester.setCode(code);
        semester.setName(name);
        semester.setStartDate(form.getStartDate());
        semester.setEndDate(form.getEndDate());
        return semester;
    }

    /** ck_semesters_dates: end_date > start_date. */
    static void checkDates(LocalDate startDate, LocalDate endDate, Map<String, String> errors) {
        if (startDate == null) {
            errors.putIfAbsent("startDate", "Vui lòng chọn ngày bắt đầu");
        }
        if (endDate == null) {
            errors.putIfAbsent("endDate", "Vui lòng chọn ngày kết thúc");
        }
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            errors.putIfAbsent("endDate", CatalogMessages.SEMESTER_DATES);
        }
    }

    private void save(Semester semester) {
        try {
            semesterRepository.saveAndFlush(semester);
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private Semester find(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CatalogMessages.SEMESTER_NOT_FOUND));
    }

    private static SemesterView toView(Semester semester) {
        return new SemesterView(semester.getId(), semester.getCode(), semester.getName(), semester.getStartDate(),
                semester.getEndDate(), semester.isCurrent());
    }
}
