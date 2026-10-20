package vn.edu.fpt.contactbook.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    @DisplayName("Migration: đọc mọi file .sql UTF-8 (có hoặc không BOM) theo thứ tự tên, đổi tên database, tách GO")
    void loadsMigrationsInNameOrder(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("V002__second.sql"), "USE [FptContactBook]\nGO\nSELECT 2;\nGO\n");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] first = "USE [FptContactBook]\r\nGO\r\n-- Tiếng Việt\r\nSELECT 1;\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + first.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(first, 0, withBom, bom.length, first.length);
        Files.write(dir.resolve("V001__first.sql"), withBom);
        Files.writeString(dir.resolve("README.txt"), "không phải migration");

        List<String> batches = SqlScriptLoader.loadMigrations(dir, "FptContactBook_test");

        assertThat(batches).containsExactly("USE [FptContactBook_test]", "-- Tiếng Việt\nSELECT 1;",
                "USE [FptContactBook_test]", "SELECT 2;");
    }

    @Test
    @DisplayName("Không có thư mục migration: danh sách rỗng")
    void missingMigrationDirectory(@TempDir Path dir) {
        assertThat(SqlScriptLoader.loadMigrations(dir.resolve("khong-co"), "FptContactBook_test")).isEmpty();
    }

    @Test
    @DisplayName("Migration thật: chạy trên database test, tạo chỉ mục audit_logs có kiểm tra tồn tại")
    void loadsRealMigrations() {
        List<String> batches = SqlScriptLoader.loadMigrations(TestDatabaseExtension.MIGRATIONS, "FptContactBook_test");
        String all = String.join("\n", batches);

        assertThat(batches.getFirst()).endsWith("USE [FptContactBook_test]");
        assertThat(all.replace("FptContactBook_test", "")).doesNotContain("FptContactBook");
        assertThat(all).contains("ix_audit_action").contains("ix_audit_entity").contains("IF NOT EXISTS");
    }
}
