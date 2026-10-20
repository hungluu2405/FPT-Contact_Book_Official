package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.RequestDetail;
import vn.edu.fpt.contactbook.model.enums.ReviewDecision;

/** excusedSessions: số buổi vắng không phép được chuyển thành vắng có phép khi duyệt đơn nghỉ. */
public record ReviewResponse(ReviewDecision decision, int excusedSessions, RequestDetail request) {
}
