package vn.edu.fpt.contactbook.service;

import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Objects;

/**
 * Người duyệt của một đơn, tính lúc truy vấn (không lưu): đơn nghỉ có lớp học phần → giảng viên lớp đó; không có lớp
 * → giảng viên chủ nhiệm lớp hành chính của sinh viên; không có chủ nhiệm → ADMIN. Đơn khác (bảng điểm, xác nhận
 * sinh viên, khác) luôn do ADMIN xử lý. Cùng quy tắc với TEACHER_QUEUE / ADMIN_QUEUE của UserRequestRepository.
 */
@Component
public class RequestReviewerResolver {

    /** teacher = null nghĩa là ADMIN duyệt. */
    public record Reviewer(Teacher teacher) {

        static final Reviewer ADMIN = new Reviewer(null);

        public boolean admin() {
            return teacher == null;
        }
    }

    public Reviewer resolve(UserRequest request) {
        if (request.getRequestType() != RequestType.LEAVE) {
            return Reviewer.ADMIN;
        }
        if (request.getClassSection() != null) {
            return new Reviewer(request.getClassSection().getTeacher());
        }
        SchoolClass schoolClass = request.getStudent() == null ? null : request.getStudent().getSchoolClass();
        if (schoolClass != null && schoolClass.getHomeroomTeacher() != null) {
            return new Reviewer(schoolClass.getHomeroomTeacher());
        }
        return Reviewer.ADMIN;
    }

    /** actor là người duyệt của đơn: đúng tài khoản giảng viên, hoặc có vai trò ADMIN với phần thuộc ADMIN. */
    public boolean isReviewer(AppUserPrincipal actor, UserRequest request) {
        Reviewer reviewer = resolve(request);
        return reviewer.admin() ? actor.hasRole(RoleCodes.ADMIN)
                : Objects.equals(reviewer.teacher().getUser().getId(), actor.getUserId());
    }
}
