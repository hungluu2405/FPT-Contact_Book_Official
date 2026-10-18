package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ExamForm;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.ScheduleConflict;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.exception.ScheduleConflictException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Exam;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.repository.ExamRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleConflictChecker.Slot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lịch thi của lớp học phần (SRS FR-SCHEDULE-02): thêm, sửa, xóa (không bảng nào tham chiếu exams).
 * ADMIN và trưởng bộ môn của môn. Kiểm tra trùng phòng (buổi học, lịch thi khác) và trùng giờ của sinh viên
 * (lịch thi và buổi học); chạy SERIALIZABLE như ClassSessionService.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ClassSessionService classSessionService;
    private final ClassSectionService classSectionService;
    private final ScheduleConflictChecker conflictChecker;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<ExamView> exams(AppUserPrincipal actor, Long sectionId, int page) {
        ClassSection section = classSectionService.findInScope(actor, sectionId);
        return examRepository.findByClassSectionIdOrderByExamDateAscStartTimeAsc(sectionId, CatalogSupport.page(page))
                .map(exam -> toView(exam, section));
    }

    @Transactional(readOnly = true)
    public ExamForm editForm(AppUserPrincipal actor, Long sectionId, Long examId) {
        classSectionService.findInScope(actor, sectionId);
        Exam exam = find(sectionId, examId);
        ExamForm form = new ExamForm();
        form.setExamType(exam.getExamType());
        form.setExamDate(exam.getExamDate());
        form.setStartTime(exam.getStartTime());
        form.setEndTime(exam.getEndTime());
        form.setRoomId(exam.getRoom() == null ? null : exam.getRoom().getId());
        form.setNote(exam.getNote());
        return form;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long create(AppUserPrincipal actor, Long sectionId, ExamForm form, String ipAddress) {
        return ClassSessionService.guarded(() -> {
            ClassSection section = classSessionService.lockForNewSchedule(actor, sectionId);
            Exam exam = new Exam();
            exam.setClassSection(section);
            apply(section, exam, form);
            examRepository.saveAndFlush(exam);
            auditService.log(actor.getUserId(), AuditActions.CREATE_EXAM, AuditActions.ENTITY_EXAM, exam.getId(),
                    detail(section, exam), ipAddress);
            return exam.getId();
        });
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void update(AppUserPrincipal actor, Long sectionId, Long examId, ExamForm form, String ipAddress) {
        ClassSessionService.guarded(() -> {
            ClassSection section = classSessionService.lockSection(actor, sectionId);
            Exam exam = find(sectionId, examId);
            apply(section, exam, form);
            examRepository.flush();
            auditService.log(actor.getUserId(), AuditActions.UPDATE_EXAM, AuditActions.ENTITY_EXAM, examId,
                    detail(section, exam), ipAddress);
            return null;
        });
    }

    @Transactional
    public void delete(AppUserPrincipal actor, Long sectionId, Long examId, String ipAddress) {
        ClassSection section = classSessionService.lockSection(actor, sectionId);
        Exam exam = find(sectionId, examId);
        String detail = detail(section, exam);
        examRepository.delete(exam);
        examRepository.flush();
        auditService.log(actor.getUserId(), AuditActions.DELETE_EXAM, AuditActions.ENTITY_EXAM, examId, detail,
                ipAddress);
    }

    /** Kiểm tra form và xung đột (bỏ qua chính lịch thi đang sửa), rồi gán vào exam. */
    private void apply(ClassSection section, Exam exam, ExamForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (form.getExamType() == null) {
            errors.put("examType", "Vui lòng chọn loại thi");
        }
        if (form.getExamDate() == null) {
            errors.put("examDate", "Vui lòng chọn ngày thi");
        }
        ClassSessionService.checkTimes(form.getStartTime(), form.getEndTime(), errors);
        Room room = classSessionService.room(form.getRoomId(), errors);
        String note = CatalogSupport.optionalText(form.getNote(), 255, "note", "Ghi chú tối đa 255 ký tự", errors);
        CatalogSupport.throwIfAny(errors);

        List<ScheduleConflict> conflicts = conflictChecker.checkExam(section, room,
                new Slot(form.getExamDate(), form.getStartTime(), form.getEndTime()), exam.getId());
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(ScheduleMessages.EXAM_CONFLICTS, conflicts);
        }
        exam.setExamType(form.getExamType());
        exam.setExamDate(form.getExamDate());
        exam.setStartTime(form.getStartTime());
        exam.setEndTime(form.getEndTime());
        exam.setRoom(room);
        exam.setNote(note);
    }

    private Exam find(Long sectionId, Long examId) {
        return examRepository.findById(examId)
                .filter(exam -> Objects.equals(exam.getClassSection().getId(), sectionId))
                .orElseThrow(() -> new NotFoundException(ScheduleMessages.EXAM_NOT_FOUND));
    }

    static ExamView toView(Exam exam, ClassSection section) {
        return new ExamView(exam.getId(), section.getCode(), section.getSubject().getCode(),
                section.getSubject().getName(), exam.getExamType(), exam.getExamDate(), exam.getStartTime(),
                exam.getEndTime(), exam.getRoom() == null ? null : exam.getRoom().getCode(), exam.getNote());
    }

    private static String detail(ClassSection section, Exam exam) {
        return "section=" + section.getCode() + ", type=" + exam.getExamType() + ", date=" + exam.getExamDate()
                + ", time=" + exam.getStartTime() + "-" + exam.getEndTime() + ", room="
                + (exam.getRoom() == null ? "-" : exam.getRoom().getCode());
    }
}
