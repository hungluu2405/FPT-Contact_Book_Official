package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tệp vượt spring.servlet.multipart.max-request-size bị Tomcat chặn (413) trước khi tới Spring MVC,
 * nên phải chạy server thật; MockMvc không áp giới hạn này.
 */
@ExtendWith(TestDatabaseExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadLimitIntegrationTest {

    private static final String BOUNDARY = "----upload-limit-test";

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Tệp trên 10 MB: trang lỗi 413 tiếng Việt, không phải trang lỗi mặc định")
    void oversizedUploadShowsFriendlyPage() throws Exception {
        String response = postLikeBrowser(multipartPart(11 * 1024 * 1024));

        assertThat(response).startsWith("HTTP/1.1 413");
        assertThat(response)
                .contains("Tệp quá lớn")
                .doesNotContain("Whitelabel");
    }

    /**
     * Gửi như trình duyệt/curl: một luồng đẩy thân yêu cầu, luồng chính đọc phản hồi. Tomcat trả 413 rồi đóng
     * kết nối khi chưa đọc hết thân, nên lỗi ghi (broken pipe) ở luồng gửi là bình thường và được bỏ qua.
     */
    private String postLikeBrowser(byte[] body) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(30_000);
            String head = "POST /profile HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Accept: text/html\r\n"
                    + "Content-Type: multipart/form-data; boundary=" + BOUNDARY + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            Thread sender = Thread.ofVirtual().start(() -> {
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(head.getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.flush();
                } catch (IOException expectedWhenServerClosesEarly) {
                    // Tomcat đã trả lời 413 và đóng kết nối.
                }
            });
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            sender.join(30_000);
            return response;
        }
    }

    private static byte[] multipartPart(int fileSize) {
        byte[] header = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"avatar\"; filename=\"big.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[header.length + fileSize + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(footer, 0, body, header.length + fileSize, footer.length);
        return body;
    }
}
