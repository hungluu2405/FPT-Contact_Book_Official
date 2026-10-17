package vn.edu.fpt.contactbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu ảnh đại diện vào {app.upload-dir}/avatars với tên UUID; không dùng tên file gốc.
 * Loại ảnh xác định bằng chữ ký đầu file (magic bytes), không tin Content-Type do trình duyệt gửi.
 */
@Slf4j
@Component
public class AvatarStorage {

    public static final String URL_PREFIX = "/avatars/";
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Pattern FILE_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final Path directory;

    public AvatarStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.directory = Path.of(uploadDir, "avatars").toAbsolutePath().normalize();
    }

    /** Kiểm tra rồi ghi ảnh; trả về tên file đã lưu. */
    public String store(AvatarUpload upload) {
        byte[] content = upload.content();
        if (content == null || content.length == 0) {
            throw new BusinessException("Tệp ảnh rỗng", "avatar");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException("Ảnh đại diện tối đa 2 MB", "avatar");
        }
        String extension = detectExtension(content, upload.contentType())
                .orElseThrow(() -> new BusinessException("Ảnh đại diện chỉ nhận JPG hoặc PNG", "avatar"));
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store avatar " + fileName, e);
        }
        return fileName;
    }

    /** Đường dẫn file ảnh nếu tên hợp lệ (chặn ../) và file tồn tại. */
    public Optional<Path> resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path file = directory.resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Xóa file do chính lớp này tạo; tên không hợp lệ thì bỏ qua. Lỗi xóa chỉ ghi log. */
    public void delete(String fileName) {
        resolve(fileName).ifPresent(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Cannot delete avatar {}", fileName, e);
            }
        });
    }

    /** Tên file từ avatar_url do lớp này sinh ra, rỗng nếu là URL khác. */
    public static Optional<String> fileNameOf(String avatarUrl) {
        return avatarUrl != null && avatarUrl.startsWith(URL_PREFIX)
                ? Optional.of(avatarUrl.substring(URL_PREFIX.length()))
                : Optional.empty();
    }

    private static Optional<String> detectExtension(byte[] content, String contentType) {
        if (startsWith(content, PNG_SIGNATURE) && "image/png".equalsIgnoreCase(contentType)) {
            return Optional.of("png");
        }
        if (startsWith(content, JPEG_SIGNATURE)
                && ("image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType))) {
            return Optional.of("jpg");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
