package vn.edu.fpt.contactbook.dto;

import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;

/**
 * Một dòng "CLB của tôi": CLB và vai trò, trạng thái của sinh viên trong CLB đó.
 */
public record MyClubRow(Long clubId, String code, String name, ClubStatus clubStatus, ClubMemberRole role,
                        ClubMemberStatus status) {
}
