package vn.edu.fpt.contactbook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.AvatarUpload;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Lưu ảnh đại diện vào {app.upload-dir}/avatars với tên UUID; không dùng tên file gốc.
 * Loại ảnh xác định bằng chữ ký đầu file (magic bytes), không tin Content-Type do trình duyệt gửi (ImageStore).
 */
@Component
public class AvatarStorage {

    public static final String URL_PREFIX = "/avatars/";
    static final int MAX_BYTES = ImageStore.MAX_BYTES;

    private final ImageStore store;

    public AvatarStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.store = new ImageStore(Path.of(uploadDir, "avatars"), "Ảnh đại diện", "avatar");
    }

    /** Kiểm tra rồi ghi ảnh; trả về tên file đã lưu. */
    public String store(AvatarUpload upload) {
        return store.store(upload.contentType(), upload.content());
    }

    /** Đường dẫn file ảnh nếu tên hợp lệ (chặn ../) và file tồn tại. */
    public Optional<Path> resolve(String fileName) {
        return store.resolve(fileName);
    }

    /** Xóa file do chính lớp này tạo; tên không hợp lệ thì bỏ qua. Lỗi xóa chỉ ghi log. */
    public void delete(String fileName) {
        store.delete(fileName);
    }

    /** Tên file từ avatar_url do lớp này sinh ra, rỗng nếu là URL khác. */
    public static Optional<String> fileNameOf(String avatarUrl) {
        return avatarUrl != null && avatarUrl.startsWith(URL_PREFIX)
                ? Optional.of(avatarUrl.substring(URL_PREFIX.length()))
                : Optional.empty();
    }
}
