package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ReportScope.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phần dùng chung của xuất báo cáo điểm danh và báo cáo điểm: dòng tiêu đề mô tả bộ lọc, tên file và audit
 * EXPORT_REPORT (loại báo cáo, bộ lọc, số dòng). entity là lớp học phần khi lọc theo lớp, môn học khi lọc theo môn,
 * không có khi xuất cả học kỳ.
 */
final class ReportExports {

    private ReportExports() {
    }

    static List<String> titles(String reportTitle, Context context) {
        List<String> titles = new ArrayList<>();
        titles.add(reportTitle);
        titles.add(ReportMessages.SEMESTER + context.semester().getCode() + " - " + context.semester().getName());
        titles.add(ReportMessages.SUBJECT + (context.subject() == null ? ReportMessages.ALL
                : context.subject().getCode() + " - " + context.subject().getName()));
        titles.add(ReportMessages.SECTION + (context.section() == null ? ReportMessages.ALL
                : context.section().getCode()));
        if (context.section() != null) {
            titles.add(ReportMessages.TEACHER + teacherName(context.section()));
        }
        return titles;
    }

    static String fileName(String prefix, Context context) {
        return ReportWorkbooks.fileName(prefix, context.semester().getCode(),
                context.section() != null ? context.section().getCode()
                        : context.subject() == null ? null : context.subject().getCode());
    }

    static void audit(AuditService auditService, AppUserPrincipal actor, String reportType, Context context,
                      int rows, String ipAddress) {
        String entityType = context.section() != null ? AuditActions.ENTITY_CLASS_SECTION
                : context.subject() != null ? AuditActions.ENTITY_SUBJECT : null;
        Long entityId = context.section() != null ? context.section().getId()
                : context.subject() != null ? context.subject().getId() : null;
        String detail = "report=" + reportType + ", semester=" + context.semester().getCode()
                + ", subject=" + (context.subject() == null ? "-" : context.subject().getCode())
                + ", section=" + (context.section() == null ? "-" : context.section().getCode())
                + ", rows=" + rows;
        auditService.log(actor.getUserId(), AuditActions.EXPORT_REPORT, entityType, entityId, detail, ipAddress);
    }

    static String teacherName(ClassSection section) {
        return section.getTeacher().getUser().getFullName();
    }

    /** Một dòng của file; List.of không nhận null, còn ô trống trong file là null. */
    static List<Object> cells(Object... values) {
        return Arrays.asList(values);
    }
}
