package vn.edu.fpt.contactbook.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseGuardTest {

    private static final String DB = "FptContactBook_test";

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://localhost:1433;databaseName=FptContactBook_test;encrypt=true",
            "jdbc:sqlserver://LOCALHOST;databaseName=FptContactBook_test",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=FptContactBook_test",
            "jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=FptContactBook_test",
            "jdbc:sqlserver://localhost\\SQLEXPRESS:1433;databaseName=FptContactBook_test",
            "jdbc:sqlserver://.;databaseName=FptContactBook_test",
            "jdbc:sqlserver://(local);databaseName=FptContactBook_test",
            "jdbc:sqlserver://;serverName=localhost;databaseName=FptContactBook_test"
    })
    @DisplayName("Cho phép database _test trên máy cục bộ")
    void acceptsLocalHosts(String url) {
        assertThatCode(() -> TestDatabaseGuard.check(url, DB)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://db.example.com:1433;databaseName=FptContactBook_test",
            "jdbc:sqlserver://10.0.0.5;databaseName=FptContactBook_test",
            "jdbc:sqlserver://localhost.example.com;databaseName=FptContactBook_test",
            "jdbc:sqlserver://;serverName=prod-sql;databaseName=FptContactBook_test",
            "jdbc:sqlserver://localhost;serverName=prod-sql;databaseName=FptContactBook_test",
            "jdbc:sqlserver://;databaseName=FptContactBook_test",
            "jdbc:mysql://localhost:3306/FptContactBook_test"
    })
    @DisplayName("Từ chối máy chủ không phải localhost hoặc URL không hợp lệ")
    void rejectsNonLocalHosts(String url) {
        assertThatThrownBy(() -> TestDatabaseGuard.check(url, DB))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FptContactBook", "FptContactBook_TEST", "FptContactBook_test_old", "master", "_testx"})
    @DisplayName("Từ chối tên database không kết thúc bằng _test")
    void rejectsNamesWithoutTestSuffix(String name) {
        assertThatThrownBy(() -> TestDatabaseGuard.check("jdbc:sqlserver://localhost;databaseName=" + name, name))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("_test");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x_test]; DROP DATABASE [FptContactBook", "my db_test"})
    @DisplayName("Từ chối tên database rỗng hoặc chứa ký tự lạ")
    void rejectsBlankOrUnsafeNames(String name) {
        assertThatThrownBy(() -> TestDatabaseGuard.check("jdbc:sqlserver://localhost", name))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Từ chối URL null")
    void rejectsNullUrl() {
        assertThatThrownBy(() -> TestDatabaseGuard.check(null, DB))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Đọc tên database từ databaseName hoặc database")
    void extractsDatabaseName() {
        assertThat(TestDatabaseGuard.extractDatabaseName(
                "jdbc:sqlserver://localhost:1433;databaseName=FptContactBook_test;encrypt=true"))
                .isEqualTo(DB);
        assertThat(TestDatabaseGuard.extractDatabaseName("jdbc:sqlserver://localhost;database=abc_test"))
                .isEqualTo("abc_test");
        assertThat(TestDatabaseGuard.extractDatabaseName("jdbc:sqlserver://localhost:1433")).isNull();
    }
}
