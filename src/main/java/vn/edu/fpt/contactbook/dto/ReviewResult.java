package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ReviewDecision;

/**
 * Kết quả duyệt: quyết định và số buổi vắng không phép đã chuyển thành vắng có phép (0 nếu từ chối hoặc đơn khác).
 */
public record ReviewResult(ReviewDecision decision, int excusedSessions) {
}
