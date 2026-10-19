package vn.edu.fpt.contactbook.service;

import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Luật thuần của câu lạc bộ (không truy cập database): vòng đời thành viên, trạng thái hoạt động theo thời gian,
 * mở đăng ký, sĩ số, ghi nhận tham dự.
 * <p>
 * Trạng thái hoạt động: database chỉ lưu PLANNED (khi tạo) và CANCELLED (khi hủy). ONGOING và DONE tính lúc hiển
 * thị: trước start_time là PLANNED, từ start_time đến trước end_time là ONGOING, từ end_time trở đi là DONE.
 */
public final class ClubRule {

    public static final int MAX_DESCRIPTION = 2000;

    private ClubRule() {
    }

    /** Bây giờ (Asia/Ho_Chi_Minh), cắt về giây cho khớp các cột datetime2(0). */
    static LocalDateTime now() {
        return AppTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Trạng thái hiển thị của hoạt động tại thời điểm now. */
    public static ClubActivityStatus state(ClubActivity activity, LocalDateTime now) {
        if (activity.getStatus() == ClubActivityStatus.CANCELLED) {
            return ClubActivityStatus.CANCELLED;
        }
        if (now.isBefore(activity.getStartTime())) {
            return ClubActivityStatus.PLANNED;
        }
        return now.isBefore(activity.getEndTime()) ? ClubActivityStatus.ONGOING : ClubActivityStatus.DONE;
    }

    /** Đã tới giờ bắt đầu (kể cả đúng giờ). */
    static boolean started(ClubActivity activity, LocalDateTime now) {
        return !now.isBefore(activity.getStartTime());
    }

    /**
     * Gửi yêu cầu tham gia: CLB phải ACTIVE; chưa có dòng thì tạo mới, REJECTED hoặc LEFT thì dùng lại dòng cũ,
     * PENDING hoặc ACTIVE thì từ chối. existing là dòng club_members hiện có (null nếu chưa có).
     */
    static void requireCanJoin(ClubStatus clubStatus, ClubMember existing) {
        if (clubStatus != ClubStatus.ACTIVE) {
            throw new BusinessException(ClubMessages.CLUB_INACTIVE);
        }
        if (existing == null) {
            return;
        }
        if (existing.getStatus() == ClubMemberStatus.PENDING) {
            throw new BusinessException(ClubMessages.ALREADY_PENDING);
        }
        if (existing.getStatus() == ClubMemberStatus.ACTIVE) {
            throw new BusinessException(ClubMessages.ALREADY_MEMBER);
        }
    }

    /** Đưa dòng (mới hoặc REJECTED/LEFT) về yêu cầu chờ duyệt. */
    static void resetToPending(ClubMember member) {
        member.setStatus(ClubMemberStatus.PENDING);
        member.setMemberRole(ClubMemberRole.MEMBER);
        member.setJoinedAt(null);
    }

    /** Sửa hoặc hủy hoạt động: chưa hủy và chưa bắt đầu. */
    static void requireEditable(ClubActivity activity, LocalDateTime now) {
        if (activity.getStatus() == ClubActivityStatus.CANCELLED) {
            throw new BusinessException(ClubMessages.ACTIVITY_CANCELLED);
        }
        if (started(activity, now)) {
            throw new BusinessException(ClubMessages.ACTIVITY_STARTED);
        }
    }

    /** Đăng ký hoặc hủy đăng ký: chưa hủy và chưa bắt đầu. */
    static void requireOpenForRegistration(ClubActivity activity, LocalDateTime now) {
        requireEditable(activity, now);
    }

    /** Còn chỗ: maxParticipants null là không giới hạn; taken đếm REGISTERED + ATTENDED. */
    static void requireCapacity(Short maxParticipants, long taken) {
        if (maxParticipants != null && taken >= maxParticipants) {
            throw new BusinessException(ClubMessages.ACTIVITY_FULL);
        }
    }

    /** Ghi nhận tham dự: chưa hủy và đã tới giờ bắt đầu. */
    static void requireCanMarkAttendance(ClubActivity activity, LocalDateTime now) {
        if (activity.getStatus() == ClubActivityStatus.CANCELLED) {
            throw new BusinessException(ClubMessages.ACTIVITY_CANCELLED);
        }
        if (!started(activity, now)) {
            throw new BusinessException(ClubMessages.ACTIVITY_NOT_STARTED);
        }
    }
}
