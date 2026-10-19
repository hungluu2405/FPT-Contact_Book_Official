package vn.edu.fpt.contactbook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Form nhập điểm của giảng viên: scores là "enrollmentId_componentId" → chữ đã nhập (trống là chưa có điểm).
 * Giữ dạng chữ để báo lỗi tiếng Việt theo từng ô thay vì lỗi chuyển kiểu.
 */
@Getter
@Setter
@NoArgsConstructor
public class GradeEntryForm {

    private Map<String, String> scores = new LinkedHashMap<>();
}
