package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleHomeResolverTest {

    @ParameterizedTest
    @CsvSource({
            "ADMIN, /admin",
            "ADMIN;TEACHER;PARENT, /admin",
            "HEAD_OF_DEPARTMENT, /teacher",
            "TEACHER;HEAD_OF_DEPARTMENT, /teacher",
            "TEACHER;STUDENT, /teacher",
            "STUDENT;PARENT, /student",
            "PARENT, /parent"
    })
    void picksHighestPriorityHome(String roles, String expected) {
        assertThat(RoleHomeResolver.homeFor(Set.of(roles.split(";")))).contains(expected);
    }

    @Test
    void noRoleHasNoHome() {
        assertThat(RoleHomeResolver.homeFor(List.of())).isEmpty();
        assertThat(RoleHomeResolver.homeFor(List.of("UNKNOWN"))).isEmpty();
    }
}
