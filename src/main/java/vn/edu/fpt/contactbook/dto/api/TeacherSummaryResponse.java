package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.UserSummaryView;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;

/** Một giáo viên trong danh sách của tổ trưởng; head = giáo viên đó cũng là tổ trưởng. */
public record TeacherSummaryResponse(Long userId, String teacherCode, String fullName, String phone, boolean active,
                                     boolean head) {

    public static TeacherSummaryResponse from(UserSummaryView view) {
        return new TeacherSummaryResponse(view.user().id(), view.user().teacherCode(), view.user().fullName(),
                view.user().phone(), view.user().active(),
                view.roles().stream().anyMatch(role -> RoleCodes.HEAD_OF_DEPARTMENT.equals(role.roleCode())));
    }
}
