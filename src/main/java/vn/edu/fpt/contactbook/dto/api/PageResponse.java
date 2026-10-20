package vn.edu.fpt.contactbook.dto.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Dạng phân trang chung của REST API; page đếm từ 0 như Pageable. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<? super S, ? extends T> mapper) {
        return new PageResponse<>(page.getContent().stream().<T>map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
