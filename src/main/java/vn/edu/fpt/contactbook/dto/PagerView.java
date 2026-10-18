package vn.edu.fpt.contactbook.dto;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Dữ liệu cho fragment phân trang dùng chung (fragments/pager.html).
 * start..end là các trang được hiện quanh trang hiện tại (đánh số từ 0);
 * params là bộ lọc hiện tại, được giữ lại trên mọi liên kết chuyển trang; pageParam là tên tham số số trang
 * (khác "page" khi một trang có hai danh sách phân trang).
 */
public record PagerView(int number, int totalPages, int start, int end, String path, Map<String, ?> params,
                        String pageParam) {

    public boolean first() {
        return number <= 0;
    }

    public boolean last() {
        return number >= totalPages - 1;
    }

    /** Đường dẫn tới trang page, kèm bộ lọc (bỏ tham số rỗng), đã mã hóa URL. */
    public String url(int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path).queryParam(pageParam, page);
        params.forEach((name, value) -> {
            if (value != null && !value.toString().isBlank()) {
                builder.queryParam(name, value);
            }
        });
        // build().encode() mã hóa giá trị như dữ liệu; builder.encode() sẽ coi "{...}" trong từ khóa là biến URI.
        return builder.build().encode().toUriString();
    }
}
