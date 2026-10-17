package vn.edu.fpt.contactbook.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Xóa và tạo lại database test từ database/fpt_contact_book.sql, đúng một lần cho mỗi lượt chạy test (JVM).
 * Gắn lên lớp test cần database bằng {@code @ExtendWith(TestDatabaseExtension.class)}.
 */
public class TestDatabaseExtension implements BeforeAllCallback {

    static final String DEFAULT_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=FptContactBook_test;encrypt=true;trustServerCertificate=true";
    static final String DEFAULT_USERNAME = "sa";
    static final Path SCRIPT = Path.of("database", "fpt_contact_book.sql");

    private static final Logger log = LoggerFactory.getLogger(TestDatabaseExtension.class);
    private static final String STORE_KEY = TestDatabaseExtension.class.getName();

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot()
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .computeIfAbsent(STORE_KEY, key -> {
                    recreateDatabase();
                    return Boolean.TRUE;
                }, Boolean.class);
    }

    private static void recreateDatabase() {
        String url = env("TEST_DB_URL", DEFAULT_URL);
        String databaseName = TestDatabaseGuard.extractDatabaseName(url);
        TestDatabaseGuard.check(url, databaseName);

        List<String> batches = SqlScriptLoader.load(SCRIPT, databaseName);
        String masterUrl = url.replaceAll("(?i)(;database(Name)?=)[^;]*", "$1master");

        try (Connection connection = DriverManager.getConnection(
                masterUrl, env("TEST_DB_USERNAME", DEFAULT_USERNAME), env("TEST_DB_PASSWORD", ""));
             Statement statement = connection.createStatement()) {
            dropIfExists(statement, url, databaseName);
            for (String batch : batches) {
                statement.execute(batch);
            }
            statement.execute("USE [master]");
        } catch (SQLException e) {
            throw new IllegalStateException("Không tạo được database test " + databaseName, e);
        }
        log.info("Recreated test database {} from {} ({} batches)", databaseName, SCRIPT, batches.size());
    }

    private static void dropIfExists(Statement statement, String url, String databaseName) throws SQLException {
        // Kiểm tra lại ngay trước lệnh DROP, không phụ thuộc vào bước gọi trước đó.
        TestDatabaseGuard.check(url, databaseName);
        statement.execute("""
                IF DB_ID(N'%1$s') IS NOT NULL
                BEGIN
                    ALTER DATABASE [%1$s] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
                    DROP DATABASE [%1$s];
                END
                """.formatted(databaseName));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
