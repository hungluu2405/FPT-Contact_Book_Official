package vn.edu.fpt.contactbook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.fpt.contactbook.dto.AttachmentUpload;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu tệp đính kèm thông báo vào {app.upload-dir}/attachments với tên UUID (như AvatarStorage), không dùng tên gốc
 * làm đường dẫn. Chỉ nhận pdf, jpg, png, docx, xlsx; một tệp hợp lệ khi đuôi, Content-Type và chữ ký đầu tệp đều
 * khớp cùng một loại. Tối đa 5 tệp, mỗi tệp tối đa 5 MB. Tệp đã ghi tự xóa nếu transaction bị rollback.
 */
@Slf4j
@Component
public class AttachmentStorage {

    public static final int MAX_FILES = 5;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Tiền tố khóa lưu trong files.file_url (khóa nội bộ, không phải URL công khai). */
    static final String KEY_PREFIX = "attachments/";

    private static final Pattern STORED_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|jpg|png|docx|xlsx)");
    private static final byte[] ZIP = {'P', 'K', 3, 4};

    /** Loại tệp được nhận: đuôi chấp nhận, Content-Type chuẩn, chữ ký đầu tệp, đuôi khi lưu. */
    enum Kind {
        PDF(Set.of("pdf"), "application/pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII), "pdf"),
        JPG(Set.of("jpg", "jpeg"), "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg"),
        PNG(Set.of("png"), "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, "png"),
        DOCX(Set.of("docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP, "docx"),
        XLSX(Set.of("xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP, "xlsx");

        final Set<String> extensions;
        final String contentType;
        final byte[] signature;
        final String storedExtension;

        Kind(Set<String> extensions, String contentType, byte[] signature, String storedExtension) {
            this.extensions = extensions;
            this.contentType = contentType;
            this.signature = signature;
            this.storedExtension = storedExtension;
        }

        static Optional<Kind> of(AttachmentUpload upload) {
            String extension = extension(upload.fileName());
            String contentType = upload.contentType() == null ? "" : upload.contentType().strip();
            return Arrays.stream(values())
                    .filter(kind -> kind.extensions.contains(extension))
                    .filter(kind -> kind.contentType.equalsIgnoreCase(contentType)
                            || (kind == JPG && "image/jpg".equalsIgnoreCase(contentType)))
                    .filter(kind -> startsWith(upload.content(), kind.signature))
                    .findFirst();
        }
    }

    /** Tệp đã ghi: khóa lưu trong files.file_url, tên hiển thị, Content-Type chuẩn, dung lượng. */
    public record Stored(String key, String fileName, String contentType, long sizeBytes) {
    }

    private final Path directory;

    public AttachmentStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.directory = Path.of(uploadDir, "attachments").toAbsolutePath().normalize();
    }

    /** Kiểm tra cả danh sách trước khi ghi bất kỳ tệp nào; lỗi chung của form (tệp không phải ô có giá trị trên form). */
    public void validate(List<AttachmentUpload> uploads) {
        if (uploads.size() > MAX_FILES) {
            throw new BusinessException(NotificationMessages.TOO_MANY_FILES);
        }
        for (AttachmentUpload upload : uploads) {
            String name = displayName(upload.fileName());
            if (upload.content() == null || upload.content().length == 0) {
                throw new BusinessException(NotificationMessages.FILE_EMPTY.formatted(name));
            }
            if (upload.content().length > MAX_BYTES) {
                throw new BusinessException(NotificationMessages.FILE_TOO_LARGE.formatted(name));
            }
            if (Kind.of(upload).isEmpty()) {
                throw new BusinessException(NotificationMessages.FILE_TYPE.formatted(name));
            }
        }
    }

    /** Ghi một tệp đã qua validate; nếu đang trong transaction thì tự xóa khi transaction rollback. */
    public Stored store(AttachmentUpload upload) {
        Kind kind = Kind.of(upload).orElseThrow(() -> new BusinessException(
                NotificationMessages.FILE_TYPE.formatted(displayName(upload.fileName()))));
        String storedName = UUID.randomUUID() + "." + kind.storedExtension;
        Path file = directory.resolve(storedName);
        try {
            Files.createDirectories(directory);
            Files.write(file, upload.content(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store attachment " + storedName, e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        delete(file);
                    }
                }
            });
        }
        return new Stored(KEY_PREFIX + storedName, displayName(upload.fileName()), kind.contentType,
                upload.content().length);
    }

    /** Đường dẫn tệp nếu khóa đúng dạng do lớp này sinh (chặn ../) và tệp tồn tại. */
    public Optional<Path> resolve(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String name = key.substring(KEY_PREFIX.length());
        if (!STORED_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = directory.resolve(name);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Tên gốc chỉ để hiển thị: bỏ phần thư mục, ký tự điều khiển, tối đa 255 ký tự (files.file_name). */
    static String displayName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "tep-dinh-kem";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static String extension(String fileName) {
        String name = displayName(fileName);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content == null || content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete attachment {}", file.getFileName(), e);
        }
    }
}
