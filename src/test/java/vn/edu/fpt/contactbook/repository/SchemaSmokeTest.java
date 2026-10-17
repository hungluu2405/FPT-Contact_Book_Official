package vn.edu.fpt.contactbook.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context khởi động với ddl-auto=validate trên FptContactBook_test (tạo từ script gốc)
 * nghĩa là toàn bộ 36 entity khớp schema.
 */
@ExtendWith(TestDatabaseExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaSmokeTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Đọc đúng 5 vai trò từ bảng roles")
    void readsFiveSeededRoles() {
        assertThat(roleRepository.findAll())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder(
                        RoleCodes.ADMIN,
                        RoleCodes.HEAD_OF_DEPARTMENT,
                        RoleCodes.TEACHER,
                        RoleCodes.STUDENT,
                        RoleCodes.PARENT);
    }
}
