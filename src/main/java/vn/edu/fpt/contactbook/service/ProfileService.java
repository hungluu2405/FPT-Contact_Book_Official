package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.fpt.contactbook.dto.AvatarUpload;
import vn.edu.fpt.contactbook.dto.ProfileForm;
import vn.edu.fpt.contactbook.dto.ProfileView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hồ sơ của chính người đang đăng nhập (userId lấy từ principal): xem, sửa email, địa chỉ, ảnh đại diện.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final AvatarStorage avatarStorage;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ProfileView getProfile(Long userId) {
        User user = findUser(userId);
        return new ProfileView(user.getPhone(), user.getFullName(), user.getEmail(), user.getGender(),
                user.getDateOfBirth(), user.getAddress(), user.getAvatarUrl(), user.getLastLoginAt(),
                accountService.roleAssignments(userId));
    }

    /**
     * Cập nhật hồ sơ. avatar = null nghĩa là giữ ảnh cũ.
     * File ảnh cũ chỉ bị xóa sau khi commit; nếu rollback thì xóa file mới vừa ghi.
     */
    @Transactional
    public void updateProfile(Long userId, ProfileForm form, AvatarUpload avatar, String ipAddress) {
        User user = findUser(userId);
        String email = blankToNull(form.getEmail());
        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new BusinessException("Email đã được người khác sử dụng", "email");
        }

        List<String> changed = new ArrayList<>();
        if (!equalsIgnoreCase(email, user.getEmail())) {
            changed.add("email");
        }
        String address = blankToNull(form.getAddress());
        if (!Objects.equals(address, user.getAddress())) {
            changed.add("address");
        }
        user.setEmail(email);
        user.setAddress(address);

        if (avatar != null) {
            String newFile = avatarStorage.store(avatar);
            Optional<String> oldFile = AvatarStorage.fileNameOf(user.getAvatarUrl());
            cleanUpFilesAfterTransaction(newFile, oldFile);
            user.setAvatarUrl(AvatarStorage.URL_PREFIX + newFile);
            changed.add("avatar");
        }

        auditService.log(userId, AuditActions.UPDATE_PROFILE, AuditActions.ENTITY_USER, userId,
                changed.isEmpty() ? null : "fields=" + String.join(",", changed), ipAddress);
    }

    private void cleanUpFilesAfterTransaction(String newFile, Optional<String> oldFile) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    oldFile.ifPresent(avatarStorage::delete);
                } else {
                    avatarStorage.delete(newFile);
                }
            }
        });
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
