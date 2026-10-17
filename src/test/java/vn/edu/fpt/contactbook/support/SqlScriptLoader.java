package vn.edu.fpt.contactbook.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Đọc script SQL Server xuất từ SSMS (UTF-16 LE có BOM), đổi tên database trong bộ nhớ
 * và tách thành các batch theo dòng chỉ chứa GO. File gốc không bị sửa.
 */
public final class SqlScriptLoader {

    static final String SOURCE_DATABASE = "FptContactBook";

    private static final Pattern GO_LINE = Pattern.compile("^\\s*GO\\s*$", Pattern.CASE_INSENSITIVE);

    private SqlScriptLoader() {
    }

    public static List<String> load(Path script, String targetDatabase) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(script);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được script: " + script.toAbsolutePath(), e);
        }
        return split(decodeUtf16Le(bytes).replace(SOURCE_DATABASE, targetDatabase));
    }

    static String decodeUtf16Le(byte[] bytes) {
        if (bytes.length < 2 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xFE) {
            throw new IllegalStateException("Script phải mã hóa UTF-16 LE có BOM");
        }
        return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
    }

    static List<String> split(String script) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\r?\\n", -1)) {
            if (GO_LINE.matcher(line).matches()) {
                addIfNotBlank(batches, current);
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        addIfNotBlank(batches, current);
        return batches;
    }

    private static void addIfNotBlank(List<String> batches, StringBuilder batch) {
        String sql = batch.toString().strip();
        if (!sql.isEmpty()) {
            batches.add(sql);
        }
    }
}
