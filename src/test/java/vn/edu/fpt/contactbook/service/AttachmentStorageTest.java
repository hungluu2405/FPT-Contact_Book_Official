package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tệp đính kèm: loại (đuôi + Content-Type + chữ ký), dung lượng, số lượng, tên lưu UUID, chặn ../, xóa khi rollback.
 */
class AttachmentStorageTest {

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @TempDir
    Path uploadDir;

    private AttachmentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new AttachmentStorage(uploadDir.toString());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("PDF, JPG, PNG, DOCX, XLSX hợp lệ được nhận")
    void acceptsAllowedTypes() {
        storage.validate(List.of(pdf("de-cuong.pdf"), upload("anh.JPG", "image/jpeg", jpeg()),
                upload("anh.png", "image/png", png()), upload("bai.docx", DOCX, zip()),
                upload("bang.xlsx", XLSX, zip())));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "đuôi không cho phép (.exe)        | virus.exe   | application/pdf",
            "đuôi PDF nhưng Content-Type ảnh   | de-cuong.pdf| image/png",
            "không có đuôi                     | de-cuong    | application/pdf"})
    @DisplayName("Đuôi hoặc Content-Type sai loại bị từ chối")
    void rejectsWrongExtensionOrContentType(String label, String fileName, String contentType) {
        assertThatThrownBy(() -> storage.validate(List.of(upload(fileName, contentType, pdfBytes()))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chỉ nhận PDF, JPG, PNG, DOCX, XLSX");
    }

    @Test
    @DisplayName("Nội dung không khớp chữ ký (HTML đổi đuôi thành .pdf) bị từ chối")
    void rejectsSignatureMismatch() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.validate(List.of(upload("x.pdf", "application/pdf", html))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Tệp trên 5 MB bị từ chối; đúng 5 MB được nhận")
    void sizeLimit() {
        byte[] exact = Arrays.copyOf(pdfBytes(), (int) AttachmentStorage.MAX_BYTES);
        byte[] over = Arrays.copyOf(pdfBytes(), (int) AttachmentStorage.MAX_BYTES + 1);

        storage.validate(List.of(upload("a.pdf", "application/pdf", exact)));
        assertThatThrownBy(() -> storage.validate(List.of(upload("b.pdf", "application/pdf", over))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vượt quá 5 MB");
    }

    @Test
    @DisplayName("Quá 5 tệp hoặc tệp rỗng bị từ chối")
    void countAndEmpty() {
        assertThatThrownBy(() -> storage.validate(Collections.nCopies(6, pdf("a.pdf"))))
                .isInstanceOf(BusinessException.class).hasMessage(NotificationMessages.TOO_MANY_FILES);
        storage.validate(Collections.nCopies(5, pdf("a.pdf")));
        assertThatThrownBy(() -> storage.validate(List.of(upload("a.pdf", "application/pdf", new byte[0]))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("rỗng");
    }

    @Test
    @DisplayName("Lưu với tên UUID trong thư mục attachments, giữ tên gốc để hiển thị, đọc lại được qua khóa")
    void storesWithUuidName() throws Exception {
        AttachmentStorage.Stored stored = storage.store(pdf("C:\\fakepath\\Đề cương.pdf"));

        assertThat(stored.key()).matches("attachments/[0-9a-f-]{36}\\.pdf");
        assertThat(stored.fileName()).isEqualTo("Đề cương.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        Path file = storage.resolve(stored.key()).orElseThrow();
        assertThat(file.getParent()).isEqualTo(uploadDir.resolve("attachments").toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(file)).isEqualTo(pdfBytes());
    }

    @Test
    @DisplayName("Khóa không đúng dạng (../, tên khác) không mở được tệp")
    void resolveRejectsTraversal() {
        assertThat(storage.resolve("attachments/../../application.properties")).isEmpty();
        assertThat(storage.resolve("avatars/x.png")).isEmpty();
        assertThat(storage.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("Transaction rollback thì tệp vừa ghi bị xóa; commit thì giữ")
    void deletesOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AttachmentStorage.Stored rolledBack = storage.store(pdf("a.pdf"));
        Path file = storage.resolve(rolledBack.key()).orElseThrow();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(file).doesNotExist();
        TransactionSynchronizationManager.clearSynchronization();

        TransactionSynchronizationManager.initSynchronization();
        AttachmentStorage.Stored committed = storage.store(pdf("b.pdf"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(storage.resolve(committed.key())).isPresent();
    }

    static AttachmentUpload pdf(String name) {
        return upload(name, "application/pdf", pdfBytes());
    }

    static AttachmentUpload upload(String name, String contentType, byte[] content) {
        return new AttachmentUpload(name, contentType, content);
    }

    static byte[] pdfBytes() {
        return "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    }

    private static byte[] zip() {
        return new byte[]{'P', 'K', 3, 4, 20, 0};
    }
}
