package vn.edu.fpt.contactbook.dto.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("Giữ số trang, cỡ trang, tổng số; ánh xạ từng phần tử")
    void mapsPage() {
        Page<Integer> page = new PageImpl<>(List.of(3, 4), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page, value -> "#" + value);

        assertThat(response.items()).containsExactly("#3", "#4");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Trang rỗng")
    void emptyPage() {
        PageResponse<Integer> response = PageResponse.from(Page.empty(PageRequest.of(0, 20)));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
