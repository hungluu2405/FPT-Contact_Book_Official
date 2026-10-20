package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.model.enums.ReviewDecision;

/** Duyệt hoặc từ chối đơn; từ chối bắt buộc có comment, tối đa 500 ký tự (RequestService kiểm tra). */
public record ReviewRequest(ReviewDecision decision, String comment) {
}
