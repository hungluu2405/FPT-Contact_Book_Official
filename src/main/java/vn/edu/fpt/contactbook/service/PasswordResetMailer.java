package vn.edu.fpt.contactbook.service;

import java.time.Duration;

/** Gửi mã quên mật khẩu tới email của người dùng; lỗi gửi ném RuntimeException. */
public interface PasswordResetMailer {

    void sendCode(String email, String fullName, String code, Duration validity);
}
