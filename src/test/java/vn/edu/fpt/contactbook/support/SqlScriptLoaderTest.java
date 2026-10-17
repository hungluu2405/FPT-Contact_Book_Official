package vn.edu.fpt.contactbook.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlScriptLoaderTest {

    @Test
    @DisplayName("Tách batch theo dòng chỉ chứa GO, bỏ batch rỗng")
    void splitsOnGoLines() {
        List<String> batches = SqlScriptLoader.split("SELECT 1\nGO\n\n  go  \r\nSELECT 2\r\nSELECT 'GO'\nGO");

        assertThat(batches).containsExactly("SELECT 1", "SELECT 2\nSELECT 'GO'");
    }

    @Test
    @DisplayName("Không tách khi GO nằm giữa câu lệnh")
    void doesNotSplitInlineGo() {
        assertThat(SqlScriptLoader.split("SELECT N'GOAL' -- GO here\nGOTO label")).hasSize(1);
    }

    @Test
    @DisplayName("Giải mã UTF-16 LE có BOM, kể cả tiếng Việt")
    void decodesUtf16LeWithBom() {
        byte[] body = "Bộ môn".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);

        assertThat(SqlScriptLoader.decodeUtf16Le(bytes)).isEqualTo("Bộ môn");
    }

    @Test
    @DisplayName("Từ chối file không có BOM UTF-16 LE")
    void rejectsMissingBom() {
        assertThatThrownBy(() -> SqlScriptLoader.decodeUtf16Le("USE [master]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Script thật: đổi mọi tham chiếu FptContactBook sang database test")
    void loadsRealScriptWithRenamedDatabase() {
        List<String> batches = SqlScriptLoader.load(TestDatabaseExtension.SCRIPT, "FptContactBook_test");
        String all = String.join("\n", batches);

        assertThat(batches).hasSizeGreaterThan(100);
        assertThat(all.replace("FptContactBook_test", "")).doesNotContain("FptContactBook");
        assertThat(all)
                .contains("CREATE DATABASE [FptContactBook_test]")
                .contains("USE [FptContactBook_test]")
                .contains("FptContactBook_test.mdf")
                .contains("FptContactBook_test_log.ldf")
                .contains("CREATE TABLE [dbo].[users]")
                .contains("Bộ môn Kỹ thuật phần mềm");
        assertThat(batches).noneMatch(batch -> batch.lines().anyMatch(line -> line.strip().equalsIgnoreCase("GO")));
    }
}
