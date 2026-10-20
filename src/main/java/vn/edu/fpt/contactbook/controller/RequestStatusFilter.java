package vn.edu.fpt.contactbook.controller;

import vn.edu.fpt.contactbook.model.enums.RequestStatus;

import java.util.Arrays;

/** Bộ lọc trạng thái của danh sách đơn cần duyệt, dùng chung cho trang web và REST API. */
public final class RequestStatusFilter {

    private RequestStatusFilter() {
    }

    /** Bỏ trống: PENDING; mã trạng thái: đúng trạng thái đó; "ALL" hoặc mã lạ: null (tất cả). */
    public static RequestStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return RequestStatus.PENDING;
        }
        return Arrays.stream(RequestStatus.values()).filter(s -> s.name().equals(status)).findFirst().orElse(null);
    }
}
