package vn.edu.fpt.contactbook.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Chạy một truy vấn IN theo từng phần, để danh sách id dài (báo cáo của ADMIN cho cả học kỳ) không vượt giới hạn
 * 2100 tham số mỗi câu lệnh của SQL Server. Kết quả các phần được nối theo thứ tự.
 */
final class IdChunks {

    static final int SIZE = 1000;

    private IdChunks() {
    }

    static <R> List<R> query(List<Long> ids, Function<List<Long>, List<R>> query) {
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() <= SIZE) {
            return query.apply(ids);
        }
        List<R> result = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += SIZE) {
            result.addAll(query.apply(ids.subList(from, Math.min(ids.size(), from + SIZE))));
        }
        return result;
    }
}
