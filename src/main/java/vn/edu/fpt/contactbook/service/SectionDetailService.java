package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.ClassSectionView;
import vn.edu.fpt.contactbook.dto.EnrollmentRow;
import vn.edu.fpt.contactbook.dto.ExamView;
import vn.edu.fpt.contactbook.dto.SessionView;
import vn.edu.fpt.contactbook.dto.StudentCandidate;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.List;

/**
 * Dữ liệu trang chi tiết lớp học phần trong MỘT transaction chỉ đọc: thông tin lớp, sinh viên, buổi học, lịch thi,
 * và (ADMIN, khi có từ khóa) sinh viên có thể ghi danh. Mỗi phần vẫn tự kiểm tra phạm vi, nhưng lớp chỉ được đọc
 * từ database một lần (các lần sau lấy từ persistence context).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SectionDetailService {

    private final ClassSectionService classSectionService;
    private final EnrollmentService enrollmentService;
    private final ClassSessionService classSessionService;
    private final ExamService examService;
    private final UserAccessPolicy userAccessPolicy;

    /** candidates rỗng nếu không phải ADMIN hoặc keyword null. */
    public record SectionDetail(ClassSectionView section, Page<EnrollmentRow> students, Page<SessionView> sessions,
                                Page<ExamView> exams, List<StudentCandidate> candidates, boolean canEnroll) {
    }

    /** status null là mọi trạng thái ghi danh; studentPage, sessionPage, examPage là số trang của từng danh sách. */
    public SectionDetail load(AppUserPrincipal actor, Long sectionId, EnrollmentStatus status, int studentPage,
                              int sessionPage, int examPage, String keyword) {
        ClassSectionView section = classSectionService.detail(actor, sectionId);
        boolean admin = userAccessPolicy.isAdmin(actor);
        List<StudentCandidate> candidates = admin && keyword != null
                ? enrollmentService.candidates(actor, sectionId, keyword) : List.of();
        return new SectionDetail(section, enrollmentService.enrollments(actor, sectionId, status, studentPage),
                classSessionService.sessions(actor, sectionId, sessionPage),
                examService.exams(actor, sectionId, examPage), candidates, admin);
    }
}
