package vn.edu.fpt.contactbook.dto;

import java.math.BigDecimal;

/**
 * Tổng hợp điểm danh của một sinh viên trong một lớp học phần.
 * totalSessions: số buổi chưa hủy của lớp (mẫu số tỷ lệ vắng); markedSessions: số buổi sinh viên đã được điểm danh
 * (mẫu số tỷ lệ chuyên cần). attendanceRate null khi chưa được điểm danh buổi nào. Tỷ lệ tính theo phần trăm.
 */
public record AttendanceSummary(long present, long late, long absentExcused, long absentUnexcused,
                                long totalSessions, BigDecimal attendanceRate, BigDecimal absentRate,
                                boolean warning) {

    public long markedSessions() {
        return present + late + absentExcused + absentUnexcused;
    }

    public long absent() {
        return absentExcused + absentUnexcused;
    }
}
