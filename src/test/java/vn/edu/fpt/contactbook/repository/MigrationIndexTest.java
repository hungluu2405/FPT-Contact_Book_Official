package vn.edu.fpt.contactbook.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.fpt.contactbook.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database test được tạo từ script gốc rồi chạy các migration trong database/migrations (review 8a, P3): bảng
 * audit_logs có thêm chỉ mục cho bộ lọc hành động và loại đối tượng của trang nhật ký.
 */
@IntegrationTest
class MigrationIndexTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("audit_logs có ix_audit_action (action, id DESC) và ix_audit_entity (entity_type, entity_id)")
    void auditLogIndexes() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT i.name + ':' + c.name + ':' + CAST(ic.is_descending_key AS varchar(1))
                FROM sys.indexes i
                JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
                WHERE i.object_id = OBJECT_ID(N'dbo.audit_logs') AND i.name IN ('ix_audit_action', 'ix_audit_entity')
                ORDER BY i.name, ic.key_ordinal
                """, String.class);

        assertThat(columns).containsExactly("ix_audit_action:action:0", "ix_audit_action:id:1",
                "ix_audit_entity:entity_type:0", "ix_audit_entity:entity_id:0");
    }
}
