package vn.edu.fpt.contactbook.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.fpt.contactbook.model.Role;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class SchemaSmokeTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Context loads and Hibernate validates schema without errors")
    void contextLoadsAndSchemaValidated() {
        assertThat(roleRepository).isNotNull();
    }

    @Test
    @DisplayName("Should successfully read 5 seeded system roles from database")
    void shouldReadFiveRolesFromDatabase() {
        List<Role> roles = roleRepository.findAll();
        assertThat(roles).isNotEmpty();

        Set<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        assertThat(roleCodes).contains(
                RoleCodes.ADMIN,
                RoleCodes.HEAD_OF_DEPARTMENT,
                RoleCodes.TEACHER,
                RoleCodes.STUDENT,
                RoleCodes.PARENT
        );
    }
}
