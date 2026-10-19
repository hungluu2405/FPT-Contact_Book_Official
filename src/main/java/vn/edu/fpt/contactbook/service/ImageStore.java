package vn.edu.fpt.contactbook.service;

import lombok.extern.slf4j.Slf4j;
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
 * Lưu ảnh JPG/PNG tối đa 2 MB vào một thư mục với tên UUID; không dùng tên file gốc. Loại ảnh xác định bằng chữ ký
 * đầu file (magic bytes) và phải khớp Content-Type. Dùng chung cho ảnh đại diện và logo câu lạc bộ; label là tên
 * loại ảnh trong thông báo lỗi, field là ô trên form nhận lỗi.
 */
@Slf4j
final class ImageStore {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Pattern FILE_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final Path directory;
    private final String label;
    private final String field;

    ImageStore(Path directory, String label, String field) {
        this.directory = directory.toAbsolutePath().normalize();
        this.label = label;
        this.field = field;
    }

    /** Kiểm tra rồi ghi ảnh; trả về tên file đã lưu. */
    String store(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Tệp ảnh rỗng", field);
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException(label + " tối đa 2 MB", field);
        }
        String extension = detectExtension(content, contentType)
                .orElseThrow(() -> new BusinessException(label + " chỉ nhận JPG hoặc PNG", field));
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store image " + fileName, e);
        }
        return fileName;
    }

    /** Đường dẫn file ảnh nếu tên hợp lệ (chặn ../) và file tồn tại. */
    Optional<Path> resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path file = directory.resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Xóa file do chính lớp này tạo; tên không hợp lệ thì bỏ qua. Lỗi xóa chỉ ghi log. */
    void delete(String fileName) {
        resolve(fileName).ifPresent(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Cannot delete image {}", fileName, e);
            }
        });
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
