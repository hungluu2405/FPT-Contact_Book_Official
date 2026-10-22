package vn.edu.fpt.contactbook.model.enums;

/** Ba trạng thái điểm danh (CHECK ck_att_status sau migration V002). Đi muộn: chọn PRESENT và ghi vào ghi chú. */
public enum AttendanceStatus {
    PRESENT,
    ABSENT_EXCUSED,
    ABSENT_UNEXCUSED
}
