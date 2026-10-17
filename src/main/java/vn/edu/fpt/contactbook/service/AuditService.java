package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;

/**
 * Nơi duy nhất ghi audit_logs (SRS FR-SYS-01). Không phụ thuộc tầng web: IP do tầng web truyền vào.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(Long userId, String action, String entityType, Long entityId, String detail, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setUser(userId == null ? null : userRepository.getReferenceById(userId));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetail(detail);
        entry.setIpAddress(ipAddress);
        auditLogRepository.save(entry);
    }
}
