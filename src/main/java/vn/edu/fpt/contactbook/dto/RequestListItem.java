package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.RequestStatus;
import vn.edu.fpt.contactbook.model.enums.RequestType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng trong danh sách đơn; fromDate, toDate, sectionCode null với đơn khác hoặc đơn không chọn lớp.
 */
public record RequestListItem(Long id, RequestType type, String title, String studentCode, String studentName,
                              String requesterName, String sectionCode, LocalDate fromDate, LocalDate toDate,
                              RequestStatus status, LocalDateTime createdAt) {
}
