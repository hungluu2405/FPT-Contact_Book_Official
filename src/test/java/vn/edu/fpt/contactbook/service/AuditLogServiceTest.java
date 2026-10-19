package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.AuditLogFilter;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bộ lọc nhật ký: chỉ ADMIN; khoảng ngày tính cả hai đầu (đến ngày → trước 00:00 ngày hôm sau); từ khóa là số điện
 * thoại thì so thêm số đã chuẩn hóa; ô trống là không lọc; đến ngày trước từ ngày là lỗi ở ô "to".
 */
class AuditLogServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD = CatalogTestData.principal(2L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
            Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    AuditLogRepository repository;
    AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(new UserAccessPolicy(mock(TeacherRepository.class)), repository);
        when(repository.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    @DisplayName("Khoảng ngày: từ 00:00 của từ ngày đến trước 00:00 của ngày sau đến ngày")
    void dateRangeInclusive() {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setFrom(LocalDate.of(2026, 10, 1));
        filter.setTo(LocalDate.of(2026, 10, 1));

        service.search(ADMIN, filter, 0);

        verify(repository).search(isNull(), isNull(), isNull(), isNull(), eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 2, 0, 0)), any());
    }

    @Test
    @DisplayName("Từ khóa số điện thoại dạng +84: mẫu LIKE theo chữ đã nhập và số đã chuẩn hóa 0...")
    void phoneKeyword() {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setKeyword(" +84900000001 ");
        filter.setAction(AuditActions.LOGIN);
        filter.setEntityType(AuditActions.ENTITY_USER);

        service.search(ADMIN, filter, 2);

        verify(repository).search(eq("%+84900000001%"), eq("0900000001"), eq(AuditActions.LOGIN),
                eq(AuditActions.ENTITY_USER), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Từ khóa là họ tên: không có số chuẩn hóa; ký tự % _ [ được escape")
    void nameKeyword() {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setKeyword("Nguyễn_%");

        service.search(ADMIN, filter, 0);

        verify(repository).search(eq("%nguyễn\\_\\%%"), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("Đến ngày trước từ ngày: lỗi ở ô to, không truy vấn")
    void invalidRange() {
        AuditLogFilter filter = new AuditLogFilter();
        filter.setFrom(LocalDate.of(2026, 10, 2));
        filter.setTo(LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> service.search(ADMIN, filter, 0))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getField()).isEqualTo("to"))
                .hasMessage(ReportMessages.AUDIT_RANGE_INVALID);
    }

    @Test
    @DisplayName("Không phải ADMIN: 403 cả danh sách và chi tiết, không truy vấn")
    void adminOnly() {
        assertThatThrownBy(() -> service.search(HEAD, new AuditLogFilter(), 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.detail(HEAD, 1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Chi tiết id không tồn tại: 404")
    void detailMissing() {
        when(repository.findWithUser(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(ADMIN, 999L)).isInstanceOf(NotFoundException.class);
    }
}
