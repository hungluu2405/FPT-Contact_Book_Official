package vn.edu.fpt.contactbook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.contactbook.dto.AvatarUpload;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Lưu logo câu lạc bộ vào {app.upload-dir}/club-logos với tên UUID (cùng quy tắc với ảnh đại diện: JPG/PNG, tối
 * đa 2 MB, kiểm tra chữ ký đầu file). clubs.logo_url lưu đường dẫn phục vụ URL_PREFIX + tên file.
 */
@Component
public class ClubLogoStorage {

    public static final String URL_PREFIX = "/club-logos/";

    private final ImageStore store;

    public ClubLogoStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.store = new ImageStore(Path.of(uploadDir, "club-logos"), "Logo", "logo");
    }

    public String store(AvatarUpload upload) {
        return store.store(upload.contentType(), upload.content());
    }

    public Optional<Path> resolve(String fileName) {
        return store.resolve(fileName);
    }

    public void delete(String fileName) {
        store.delete(fileName);
    }

    /** Tên file từ logo_url do lớp này sinh ra, rỗng nếu là URL khác. */
    public static Optional<String> fileNameOf(String logoUrl) {
        return logoUrl != null && logoUrl.startsWith(URL_PREFIX)
                ? Optional.of(logoUrl.substring(URL_PREFIX.length()))
                : Optional.empty();
    }
}
