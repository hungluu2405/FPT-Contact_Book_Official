package vn.edu.fpt.contactbook.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.fpt.contactbook.dto.AttendanceForm;
import vn.edu.fpt.contactbook.dto.AttendanceSaveResult;
import vn.edu.fpt.contactbook.dto.AttendanceSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.enums.AttendanceStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;
import vn.edu.fpt.contactbook.service.AttendanceService;
import vn.edu.fpt.contactbook.service.AttendanceViewService;
import vn.edu.fpt.contactbook.service.AttendanceViewService.SectionAttendance;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.PageUtils;

/**
 * Dựng model chung cho bảng điểm danh một buổi (common/attendance-sheet.html) và thống kê điểm danh của lớp
 * (common/section-attendance.html), dùng cho cả trang giảng viên và trang quản lý lớp học phần.
 * baseUrl là đường dẫn của lớp: /teacher/sections/{id} hoặc /admin/sections/{id}. Phạm vi kiểm tra ở service.
 */
@Component
@RequiredArgsConstructor
class AttendancePages {

    private final AttendanceService attendanceService;
    private final AttendanceViewService attendanceViewService;

    /** canMarkNew: được điểm danh lần đầu (giảng viên); canAdjust: được điều chỉnh buổi đã điểm danh. */
    String section(Model model, AppUserPrincipal actor, Access access, Long sectionId, int sessionPage,
                   int studentPage, String baseUrl, String statsUrl, boolean canMarkNew, boolean canAdjust) {
        SectionAttendance data = attendanceViewService.section(actor, access, sectionId, sessionPage, studentPage);
        model.addAttribute("item", data.section());
        model.addAttribute("sessions", data.sessions());
        model.addAttribute("sessionPager", PageUtils.pager(data.sessions(), statsUrl,
                PageUtils.params("studentPage", studentPage), "sessionPage"));
        model.addAttribute("students", data.students());
        model.addAttribute("studentPager", PageUtils.pager(data.students(), statsUrl,
                PageUtils.params("sessionPage", sessionPage), "studentPage"));
        model.addAttribute("totalSessions", data.totalSessions());
        model.addAttribute("threshold", attendanceViewService.thresholdPercent());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("canMarkNew", canMarkNew);
        model.addAttribute("canAdjust", canAdjust);
        model.addAttribute("today", AppTime.today());
        return "common/section-attendance";
    }

    /**
     * Mở bảng điểm danh: trạng thái, ghi chú đã lưu điền sẵn vào form; buổi chưa điểm danh thì học sinh có đơn nghỉ
     * đã duyệt được chọn sẵn "Vắng có phép".
     */
    String sheet(Model model, AppUserPrincipal actor, Access access, Long sectionId, Long sessionId,
                 String baseUrl, boolean canSave) {
        AttendanceSheet sheet = attendanceService.sheet(actor, access, sectionId, sessionId);
        AttendanceForm form = new AttendanceForm();
        for (AttendanceSheet.Row row : sheet.rows()) {
            if (row.status() != null) {
                form.getStatuses().put(row.studentId(), row.status());
            } else if (row.approvedLeave()) {
                form.getStatuses().put(row.studentId(), AttendanceStatus.ABSENT_EXCUSED);
            }
            if (row.note() != null) {
                form.getNotes().put(row.studentId(), row.note());
            }
        }
        model.addAttribute("form", form);
        return sheetView(model, sheet, baseUrl, canSave);
    }

    /**
     * Lưu bảng điểm danh. Thành công → về trang lớp kèm thông báo; lỗi → hiện lại bảng với lựa chọn đã nhập.
     * Trạng thái không hợp lệ (sửa tay request) là lỗi chung của form. canSave = false (trưởng bộ môn) → 403;
     * service vẫn tự kiểm tra quyền ghi.
     */
    String save(Model model, AppUserPrincipal actor, Access access, Long sectionId, Long sessionId,
                AttendanceForm form, BindingResult bindingResult, String ipAddress, String baseUrl, boolean canSave,
                RedirectAttributes redirectAttributes) {
        // Kiểm tra phạm vi, buổi học và quyền ghi trước, kể cả khi form có lỗi (người chỉ xem nhận 403).
        AttendanceSheet sheet = attendanceService.sheet(actor, access, sectionId, sessionId);
        if (!canSave) {
            throw new AccessDeniedException("Attendance is read-only for this user");
        }
        if (bindingResult.getFieldErrors().stream().anyMatch(e -> !"reason".equals(e.getField()))) {
            bindingResult.reject("invalidStatus", "Trạng thái điểm danh không hợp lệ, vui lòng chọn lại");
        }
        if (!bindingResult.hasErrors()) {
            try {
                AttendanceSaveResult result = attendanceService.save(actor, access, sectionId, sessionId, form,
                        ipAddress);
                redirectAttributes.addFlashAttribute("successMessage", successMessage(result, sheet));
                return "redirect:" + baseUrl;
            } catch (BusinessException e) {
                FormErrors.apply(bindingResult, e);
                // Danh sách sinh viên có thể vừa đổi: đọc lại để hiện đúng lớp hiện tại.
                sheet = attendanceService.sheet(actor, access, sectionId, sessionId);
            }
        }
        return sheetView(model, sheet, baseUrl, true);
    }

    private static String successMessage(AttendanceSaveResult result, AttendanceSheet sheet) {
        if (result.firstTime()) {
            return "Đã lưu điểm danh buổi " + sheet.sessionNo() + " (" + result.changed() + " sinh viên)";
        }
        return result.changed() == 0 ? "Không có thay đổi nào để lưu"
                : "Đã điều chỉnh điểm danh của " + result.changed() + " sinh viên";
    }

    /** Quá hạn của giáo viên (sheet.locked) thì chỉ xem, kèm thông báo hạn. */
    private String sheetView(Model model, AttendanceSheet sheet, String baseUrl, boolean canSave) {
        model.addAttribute("sheet", sheet);
        model.addAttribute("statuses", AttendanceStatus.values());
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("canSave", canSave && !sheet.locked());
        return "common/attendance-sheet";
    }
}
