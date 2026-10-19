package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Objects;

/**
 * Phạm vi lớp học phần cho điểm danh (CLAUDE.md mục 5), kiểm tra ở tầng service kể cả khi gõ URL:
 * TEACHER chỉ lớp có class_sections.teacher_id là hồ sơ giảng viên của mình (lớp khác hoặc không tồn tại → 403,
 * để không dò được id); MANAGER theo ClassSectionService.findInScope (ADMIN mọi lớp, trưởng bộ môn lớp thuộc bộ môn
 * mình). Ghi qua đường MANAGER chỉ ADMIN (FR-ATT-03); trưởng bộ môn chỉ xem.
 */
@Component
@RequiredArgsConstructor
public class AttendanceScope {

    /** Đường truy cập: trang giảng viên (/teacher) hay trang quản lý lớp học phần (/admin/sections). */
    public enum Access { TEACHER, MANAGER }

    private final TeacherRepository teacherRepository;
    private final ClassSectionRepository classSectionRepository;
    private final ClassSectionService classSectionService;
    private final UserAccessPolicy userAccessPolicy;

    ClassSection section(AppUserPrincipal actor, Access access, Long sectionId, boolean write) {
        if (access == Access.MANAGER) {
            ClassSection section = classSectionService.findInScope(actor, sectionId);
            if (write && !userAccessPolicy.isAdmin(actor)) {
                throw new AccessDeniedException("Only admin can adjust attendance from section management");
            }
            return section;
        }
        Teacher teacher = teacher(actor);
        ClassSection section = classSectionRepository.findById(sectionId).orElse(null);
        if (section == null || !Objects.equals(section.getTeacher().getId(), teacher.getId())) {
            throw new AccessDeniedException("Class section is not taught by this teacher");
        }
        return section;
    }

    /** Hồ sơ giảng viên của người đăng nhập; chưa có hồ sơ (ví dụ trưởng bộ môn thuần quản lý) → 403. */
    Teacher teacher(AppUserPrincipal actor) {
        return teacherRepository.findByUserId(actor.getUserId())
                .orElseThrow(() -> new AccessDeniedException("No teacher profile"));
    }
}
