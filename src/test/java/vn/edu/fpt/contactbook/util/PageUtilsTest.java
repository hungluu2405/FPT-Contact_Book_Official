package vn.edu.fpt.contactbook.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.fpt.contactbook.dto.PagerView;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageUtilsTest {

    @ParameterizedTest(name = "trang {0}/{1} → hiện {2}..{3}")
    @CsvSource({
            "0, 1, 0, 0",
            "0, 10, 0, 2",
            "1, 10, 0, 3",
            "5, 10, 3, 7",
            "9, 10, 7, 9",
            "0, 0, 0, 0"
    })
    void windowAroundCurrentPage(int number, int totalPages, int start, int end) {
        PagerView pager = PageUtils.pager(page(number, totalPages), "/x", Map.of());

        assertThat(pager.start()).isEqualTo(start);
        assertThat(pager.end()).isEqualTo(end);
        assertThat(pager.first()).isEqualTo(number == 0);
    }

    @Test
    void urlKeepsFiltersAndSkipsBlankValues() {
        PagerView pager = PageUtils.pager(page(1, 5), "/admin/users",
                PageUtils.params("keyword", "Nguyễn Văn", "role", null, "departmentId", 1L, "status", " "));

        assertThat(pager.url(2)).isEqualTo("/admin/users?page=2&keyword=Nguy%E1%BB%85n%20V%C4%83n&departmentId=1");
        assertThat(PageUtils.pager(page(0, 2), "/admin/catalog/rooms", PageUtils.params()).url(1))
                .isEqualTo("/admin/catalog/rooms?page=1");
    }

    @Test
    void urlEncodesSpecialCharacters() {
        PagerView pager = PageUtils.pager(page(0, 2), "/x", PageUtils.params("keyword", "a&b=c% {x}#"));

        assertThat(pager.url(1)).isEqualTo("/x?page=1&keyword=a%26b%3Dc%25%20%7Bx%7D%23");
    }

    @Test
    void paramsRequirePairs() {
        assertThatThrownBy(() -> PageUtils.params("keyword")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Page<String> page(int number, int totalPages) {
        int size = 20;
        long total = (long) totalPages * size;
        return new PageImpl<>(Collections.nCopies(totalPages == 0 ? 0 : size, "x"), PageRequest.of(number, size),
                total);
    }
}
