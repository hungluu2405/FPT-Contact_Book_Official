package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.AuditLogFilter;
import vn.edu.fpt.contactbook.dto.AuditLogView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.LikePatterns;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.time.LocalDateTime;

/**
 * Xem nhật ký hệ thống (SRS FR-SYS-01), chỉ ADMIN và CHỈ ĐỌC: không có thao tác sửa hay xóa audit_logs. Ghi nhật ký
 * vẫn chỉ qua AuditService. Khoảng ngày tính theo giờ Asia/Ho_Chi_Minh, đúng múi giờ khi ghi (AppTime).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final UserAccessPolicy userAccessPolicy;
    private final AuditLogRepository auditLogRepository;

    /** Đến ngày trước từ ngày → BusinessException ở ô "to". */
    public Page<AuditLogView> search(AppUserPrincipal actor, AuditLogFilter filter, int page) {
        userAccessPolicy.requireAdmin(actor);
        if (filter.getFrom() != null && filter.getTo() != null && filter.getTo().isBefore(filter.getFrom())) {
            throw new BusinessException(ReportMessages.AUDIT_RANGE_INVALID, "to");
        }
        String keyword = CatalogSupport.trimToNull(filter.getKeyword());
        LocalDateTime from = filter.getFrom() == null ? null : filter.getFrom().atStartOfDay();
        LocalDateTime toExclusive = filter.getTo() == null ? null : filter.getTo().plusDays(1).atStartOfDay();
        return auditLogRepository.search(LikePatterns.containsIgnoreCase(keyword),
                        keyword == null ? null : PhoneUtils.normalize(keyword).orElse(null),
                        CatalogSupport.trimToNull(filter.getAction()), CatalogSupport.trimToNull(filter.getEntityType()),
                        from, toExclusive, CatalogSupport.page(page))
                .map(AuditLogService::toView);
    }

    public AuditLogView detail(AppUserPrincipal actor, Long id) {
        userAccessPolicy.requireAdmin(actor);
        return auditLogRepository.findWithUser(id).map(AuditLogService::toView)
                .orElseThrow(() -> new NotFoundException(ReportMessages.AUDIT_NOT_FOUND));
    }

    private static AuditLogView toView(AuditLog log) {
        User user = log.getUser();
        return new AuditLogView(log.getId(), log.getCreatedAt(), user == null ? null : user.getId(),
                user == null ? null : user.getFullName(), user == null ? null : user.getPhone(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDetail(), log.getIpAddress());
    }
}
