package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;

/**
 * otherKey: số điện thoại phụ huynh (trang của sinh viên) hoặc mã sinh viên (trang của phụ huynh).
 */
@Getter
@Setter
@NoArgsConstructor
public class ParentLinkForm {

    private String otherKey;
    private ParentRelationship relationship = ParentRelationship.OTHER;
    private boolean primary;
}
