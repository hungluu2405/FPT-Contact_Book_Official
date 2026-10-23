package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.ClubView;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;

import java.time.LocalDate;

/** Một câu lạc bộ cho API mobile; bỏ đường dẫn logo và id người dùng của giáo viên phụ trách (chỉ dùng trên web). */
public record ClubResponse(Long id, String code, String name, String description, LocalDate foundedDate,
                           String advisorName, ClubStatus status, long memberCount, String presidentName) {

    public static ClubResponse from(ClubView club) {
        return new ClubResponse(club.id(), club.code(), club.name(), club.description(), club.foundedDate(),
                club.advisorName(), club.status(), club.memberCount(), club.presidentName());
    }
}
