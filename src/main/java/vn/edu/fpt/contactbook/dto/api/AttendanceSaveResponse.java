package vn.edu.fpt.contactbook.dto.api;

import vn.edu.fpt.contactbook.dto.AttendanceSheet;

/** firstTime: lần điểm danh đầu của buổi; changed: số sinh viên được lưu hoặc điều chỉnh; sheet: bảng sau khi lưu. */
public record AttendanceSaveResponse(boolean firstTime, int changed, AttendanceSheet sheet) {
}
