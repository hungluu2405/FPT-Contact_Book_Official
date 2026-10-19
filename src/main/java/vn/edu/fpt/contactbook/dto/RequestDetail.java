package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết đơn cho người được xem. reviewerName null nghĩa là ADMIN duyệt. canCancel: người xem là người gửi và đơn
 * còn chờ duyệt; canReview: người xem là người duyệt (ở trang duyệt) và đơn còn chờ duyệt.
 */
public record RequestDetail(Long id, RequestType type, String title, String reason, String studentCode,
                            String studentName, String requesterName, boolean sentByParent, String sectionLabel,
                            LocalDate fromDate, LocalDate toDate, RequestStatus status, LocalDateTime createdAt,
                            LocalDateTime updatedAt, String reviewerName, List<FileItem> files,
                            List<ReviewItem> reviews, boolean canCancel, boolean canReview) {

    public record FileItem(Long id, String fileName, Long sizeBytes) {
    }

    public record ReviewItem(String reviewerName, ReviewDecision decision, String comment,
                             LocalDateTime reviewedAt) {
    }
}
