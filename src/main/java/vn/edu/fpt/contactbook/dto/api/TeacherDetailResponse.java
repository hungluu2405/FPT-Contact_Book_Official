package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.UserDetailView;
import vn.edu.fpt.contactbook.dto.UserRoleView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết giáo viên cho tổ trưởng: chỉ thông tin công việc và liên lạc như trang web của tổ trưởng; không có ngày
 * sinh, địa chỉ hay cờ đổi mật khẩu. roles là mã vai trò, không trùng.
 */
public record TeacherDetailResponse(Long userId, String teacherCode, String fullName, String academicTitle,
                                    String departmentName, String phone, String email, boolean active,
                                    LocalDateTime lastLoginAt, List<String> roles) {

    public static TeacherDetailResponse from(UserDetailView view) {
        UserDetailView.TeacherProfile teacher = view.teacher();
        return new TeacherDetailResponse(view.id(), teacher == null ? null : teacher.teacherCode(), view.fullName(),
                teacher == null ? null : teacher.academicTitle(), teacher == null ? null : teacher.departmentName(),
                view.phone(), view.email(), view.active(), view.lastLoginAt(),
                view.roles().stream().map(UserRoleView::roleCode).distinct().toList());
    }
}
