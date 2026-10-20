package vn.edu.fpt.contactbook.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApiMustChangePasswordFilterTest {

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "POST, /api/v1/auth/login, true",
            "POST, /api/v1/auth/refresh, true",
            "POST, /api/v1/auth/logout, true",
            "GET, /api/v1/me, true",
            "POST, /api/v1/me/password, true",
            "POST, /api/v1/me, false",
            "GET, /api/v1/me/password, false",
            "GET, /api/v1/me/schedule, false",
            "GET, /api/v1/grades, false"
    })
    void exemptPaths(String method, String path, boolean exempt) {
        assertThat(ApiMustChangePasswordFilter.isExempt(method, path)).isEqualTo(exempt);
    }
}
