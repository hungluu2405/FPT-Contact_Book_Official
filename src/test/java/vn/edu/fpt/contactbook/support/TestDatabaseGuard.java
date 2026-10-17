package vn.edu.fpt.contactbook.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rào chắn an toàn trước mọi lệnh DROP DATABASE của test suite (CLAUDE.md mục 8).
 * Chỉ cho phép database có tên kết thúc bằng "_test" trên máy chủ cục bộ.
 */
public final class TestDatabaseGuard {

    static final String REQUIRED_SUFFIX = "_test";

    private static final String JDBC_PREFIX = "jdbc:sqlserver://";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", ".", "(local)");
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private TestDatabaseGuard() {
    }

    /**
     * Ném {@link IllegalStateException} nếu tên database hoặc máy chủ trong URL không an toàn để xóa.
     */
    public static void check(String jdbcUrl, String databaseName) {
        if (databaseName == null || !SAFE_NAME.matcher(databaseName).matches()) {
            throw new IllegalStateException("Từ chối DROP: tên database không hợp lệ: " + databaseName);
        }
        if (!databaseName.endsWith(REQUIRED_SUFFIX)) {
            throw new IllegalStateException(
                    "Từ chối DROP: tên database phải kết thúc bằng '" + REQUIRED_SUFFIX + "': " + databaseName);
        }
        String host = extractHost(jdbcUrl);
        if (!LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Từ chối DROP: máy chủ không phải localhost: " + host);
        }
    }

    /**
     * Lấy tên máy chủ từ URL dạng jdbc:sqlserver://host[\instance][:port][;key=value]...
     * Nếu URL có thuộc tính serverName thì thuộc tính đó cũng phải là máy cục bộ.
     */
    static String extractHost(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.regionMatches(true, 0, JDBC_PREFIX, 0, JDBC_PREFIX.length())) {
            throw new IllegalStateException("Từ chối DROP: URL không phải SQL Server JDBC");
        }
        String rest = jdbcUrl.substring(JDBC_PREFIX.length());
        int semicolon = rest.indexOf(';');
        String serverPart = semicolon >= 0 ? rest.substring(0, semicolon) : rest;
        String properties = semicolon >= 0 ? rest.substring(semicolon + 1) : "";

        String host = hostOf(serverPart);
        String serverNameProperty = property(properties, "serverName");
        if (serverNameProperty != null) {
            String propertyHost = hostOf(serverNameProperty);
            if (!host.isEmpty() && !host.equalsIgnoreCase(propertyHost)) {
                throw new IllegalStateException("Từ chối DROP: URL có hai máy chủ khác nhau");
            }
            host = propertyHost;
        }
        if (host.isEmpty()) {
            throw new IllegalStateException("Từ chối DROP: URL không có máy chủ");
        }
        return host;
    }

    /** Lấy giá trị databaseName (hoặc database) trong URL, null nếu không có. */
    public static String extractDatabaseName(String jdbcUrl) {
        int semicolon = jdbcUrl == null ? -1 : jdbcUrl.indexOf(';');
        if (semicolon < 0) {
            return null;
        }
        String properties = jdbcUrl.substring(semicolon + 1);
        String name = property(properties, "databaseName");
        return name != null ? name : property(properties, "database");
    }

    private static String hostOf(String server) {
        String host = server.trim();
        int backslash = host.indexOf('\\');
        if (backslash >= 0) {
            host = host.substring(0, backslash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.trim();
    }

    private static String property(String properties, String key) {
        for (String pair : properties.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).trim().equalsIgnoreCase(key)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return null;
    }
}
