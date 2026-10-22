package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpPasswordResetMailerTest {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "::1", " LOCALHOST "})
    @DisplayName("SMTP trên máy này luôn được phép")
    void allowsLocalHosts(String host) {
        assertThatCode(() -> SmtpPasswordResetMailer.checkHost(host, false)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"smtp.gmail.com", "mail.fpt.edu.vn", "10.0.0.5", "localhost.example.com", ""})
    @DisplayName("SMTP ngoài máy này bị chặn khi chưa bật app.mail.allow-external-host")
    void rejectsExternalHosts(String host) {
        assertThatThrownBy(() -> SmtpPasswordResetMailer.checkHost(host, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Không có host cũng bị chặn; bật cờ thì SMTP ngoài được phép")
    void externalHostNeedsFlag() {
        assertThatThrownBy(() -> SmtpPasswordResetMailer.checkHost(null, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> SmtpPasswordResetMailer.checkHost("smtp.gmail.com", true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Email gửi từ địa chỉ cấu hình, có tên người nhận, mã và thời hạn")
    void sendsPlainTextCode() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpPasswordResetMailer mailer = new SmtpPasswordResetMailer(sender, "localhost", false,
                "no-reply@contactbook.local");

        mailer.sendCode("student@fpt.edu.vn", "Lê Minh Sinh", "042917", Duration.ofMinutes(10));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@contactbook.local");
        assertThat(message.getTo()).containsExactly("student@fpt.edu.vn");
        assertThat(message.getSubject()).isEqualTo(SmtpPasswordResetMailer.SUBJECT);
        assertThat(message.getText()).contains("Lê Minh Sinh", "042917", "10 phút");
    }
}
