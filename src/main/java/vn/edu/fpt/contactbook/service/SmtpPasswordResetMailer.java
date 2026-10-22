package vn.edu.fpt.contactbook.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Gửi mã quên mật khẩu qua SMTP (spring.mail.*). Dữ liệu mẫu có email thật dạng @fpt.edu.vn nên mặc định chỉ được gửi
 * qua SMTP trên máy này (Mailpit ở localhost:1025); muốn dùng SMTP khác phải bật app.mail.allow-external-host.
 */
@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    static final String SUBJECT = "Mã đặt lại mật khẩu - Sổ liên lạc điện tử";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender mailSender,
                                   @Value("${spring.mail.host}") String host,
                                   @Value("${app.mail.allow-external-host:false}") boolean allowExternalHost,
                                   @Value("${app.mail.from}") String from) {
        checkHost(host, allowExternalHost);
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Ném IllegalStateException (ứng dụng không khởi động) khi SMTP không ở máy này mà chưa cho phép. */
    static void checkHost(String host, boolean allowExternalHost) {
        if (!allowExternalHost && (host == null || !LOCAL_HOSTS.contains(host.trim().toLowerCase()))) {
            throw new IllegalStateException("spring.mail.host must be localhost unless app.mail.allow-external-host"
                    + " is true, got: " + host);
        }
    }

    @Override
    public void sendCode(String email, String fullName, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText(body(fullName, code, validity));
        mailSender.send(message);
    }

    static String body(String fullName, String code, Duration validity) {
        return "Xin chào " + fullName + ",\n\n"
                + "Mã xác nhận để đặt lại mật khẩu của bạn là: " + code + "\n\n"
                + "Mã có hiệu lực trong khoảng " + validity.toMinutes() + " phút. Không chia sẻ mã này với bất kỳ ai.\n"
                + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này; mật khẩu hiện tại vẫn giữ nguyên.\n\n"
                + "Sổ liên lạc điện tử";
    }
}
