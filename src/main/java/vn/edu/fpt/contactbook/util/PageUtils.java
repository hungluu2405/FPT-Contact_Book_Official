package vn.edu.fpt.contactbook.util;

import org.springframework.data.domain.Page;
import vn.edu.fpt.contactbook.dto.PagerView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tạo dữ liệu phân trang cho danh sách: chỉ hiện trang hiện tại ±WINDOW để thanh phân trang
 * không dài ra theo số trang.
 */
public final class PageUtils {

    public static final int WINDOW = 2;

    private PageUtils() {
    }

    public static PagerView pager(Page<?> page, String path, Map<String, ?> params) {
        return pager(page, path, params, "page");
    }

    /** Như pager(page, path, params) với tên tham số số trang tùy chọn. */
    public static PagerView pager(Page<?> page, String path, Map<String, ?> params, String pageParam) {
        int start = Math.max(0, page.getNumber() - WINDOW);
        int end = Math.max(start, Math.min(page.getTotalPages() - 1, page.getNumber() + WINDOW));
        return new PagerView(page.getNumber(), page.getTotalPages(), start, end, path, params, pageParam);
    }

    /** Bộ lọc dạng tên, giá trị, tên, giá trị...; giá trị được phép null (Map.of không nhận null). */
    public static Map<String, Object> params(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected name/value pairs");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            params.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return params;
    }
}
