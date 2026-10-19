package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lưu ảnh dùng chung (ImageStore) qua ClubLogoStorage: thư mục riêng club-logos, lỗi gắn ô "logo" với nhãn "Logo",
 * URL phục vụ /club-logos/, chặn tên file không do lớp này sinh ra.
 */
class ImageStoreTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @TempDir Path uploadDir;

    @Test
    @DisplayName("Logo lưu vào club-logos với tên UUID, tách khỏi thư mục ảnh đại diện")
    void storesUnderOwnDirectory() {
        ClubLogoStorage storage = new ClubLogoStorage(uploadDir.toString());

        String name = storage.store(new AvatarUpload("image/jpeg", JPEG));

        assertThat(name).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(Files.isRegularFile(uploadDir.resolve("club-logos").resolve(name))).isTrue();
        assertThat(Files.exists(uploadDir.resolve("avatars"))).isFalse();
        assertThat(storage.resolve(name)).isPresent();
        storage.delete(name);
        assertThat(storage.resolve(name)).isEmpty();
    }

    @Test
    @DisplayName("Lỗi logo gắn ô logo với nhãn Logo")
    void errorsUseLogoField() {
        ClubLogoStorage storage = new ClubLogoStorage(uploadDir.toString());

        assertThatThrownBy(() -> storage.store(new AvatarUpload("image/png", JPEG)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("logo", "Logo chỉ nhận JPG hoặc PNG"));
    }

    @Test
    @DisplayName("URL và tên file: chỉ nhận đường dẫn /club-logos/ và tên UUID hợp lệ")
    void namesAndUrls() {
        ClubLogoStorage storage = new ClubLogoStorage(uploadDir.toString());

        assertThat(ClubLogoStorage.fileNameOf("/club-logos/abc.png")).contains("abc.png");
        assertThat(ClubLogoStorage.fileNameOf("/avatars/abc.png")).isEmpty();
        assertThat(ClubLogoStorage.fileNameOf(null)).isEmpty();
        assertThat(storage.resolve("../avatars/x.png")).isEmpty();
    }
}
