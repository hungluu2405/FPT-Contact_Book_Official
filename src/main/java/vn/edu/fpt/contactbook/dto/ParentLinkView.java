package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ParentRelationship;

/**
 * Một liên kết phụ huynh – sinh viên nhìn từ phía người đang xem.
 * profileId: parents.id (trang của sinh viên) hoặc students.id (trang của phụ huynh).
 */
public record ParentLinkView(Long profileId, Long userId, String fullName, String phone, String studentCode,
                             ParentRelationship relationship, boolean primary) {
}
