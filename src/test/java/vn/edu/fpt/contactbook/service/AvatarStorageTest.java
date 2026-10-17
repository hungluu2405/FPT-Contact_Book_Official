package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    @TempDir
    Path uploadDir;

    private AvatarStorage storage;

    @BeforeEach
    void setUp() {
        storage = new AvatarStorage(uploadDir.toString());
    }

    @Test
    @DisplayName("Lưu PNG và JPG với tên UUID, đọc lại được")
    void storesPngAndJpegUnderUuidNames() throws Exception {
        String png = storage.store(new AvatarUpload("image/png", PNG));
        String jpg = storage.store(new AvatarUpload("image/jpeg", JPEG));

        assertThat(png).matches("[0-9a-f-]{36}\\.png");
        assertThat(jpg).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(Files.readAllBytes(storage.resolve(png).orElseThrow())).isEqualTo(PNG);
        assertThat(storage.resolve(jpg)).isPresent();
    }

    @Test
    @DisplayName("Từ chối file giả mạo: Content-Type là PNG nhưng nội dung không phải")
    void rejectsSpoofedContent() {
        byte[] gif = "GIF89a....".getBytes();
        assertThatThrownBy(() -> storage.store(new AvatarUpload("image/png", gif)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("field", "avatar");
    }

    @Test
    @DisplayName("Từ chối nội dung PNG nhưng khai báo loại khác")
    void rejectsMismatchedContentType() {
        assertThatThrownBy(() -> storage.store(new AvatarUpload("image/gif", PNG)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.store(new AvatarUpload(null, JPEG)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Biên dung lượng: đúng 2 MB nhận, 2 MB + 1 byte từ chối")
    void sizeBoundary() {
        byte[] max = Arrays.copyOf(PNG, AvatarStorage.MAX_BYTES);
        byte[] tooBig = Arrays.copyOf(PNG, AvatarStorage.MAX_BYTES + 1);

        assertThat(storage.store(new AvatarUpload("image/png", max))).endsWith(".png");
        assertThatThrownBy(() -> storage.store(new AvatarUpload("image/png", tooBig)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    @DisplayName("Từ chối file rỗng")
    void rejectsEmpty() {
        assertThatThrownBy(() -> storage.store(new AvatarUpload("image/png", new byte[0])))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.png", "..\\secret.png", "a.png", "00000000-0000-0000-0000-000000000000.gif",
            "00000000-0000-0000-0000-000000000000.png/../x"})
    @DisplayName("Không trả file với tên không phải UUID (chặn ../)")
    void resolveRejectsUnsafeNames(String name) {
        assertThat(storage.resolve(name)).isEmpty();
    }

    @Test
    @DisplayName("Xóa file đã lưu; tên lạ thì bỏ qua")
    void deletesStoredFile() {
        String name = storage.store(new AvatarUpload("image/png", PNG));

        storage.delete(name);
        storage.delete("../whatever.png");

        assertThat(storage.resolve(name)).isEmpty();
    }

    @Test
    void fileNameOfOnlyAcceptsOwnUrls() {
        assertThat(AvatarStorage.fileNameOf("/avatars/x.png")).contains("x.png");
        assertThat(AvatarStorage.fileNameOf("https://example.com/a.png")).isEmpty();
        assertThat(AvatarStorage.fileNameOf(null)).isEmpty();
    }
}
