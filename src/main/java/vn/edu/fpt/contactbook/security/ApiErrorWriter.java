package vn.edu.fpt.contactbook.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.exception.ApiErrorCode;
import vn.edu.fpt.contactbook.exception.ApiErrors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả lỗi JSON cho chuỗi bảo mật /api/** (thiếu token, token sai hoặc hết hạn, sai quyền), thay cho trang
 * đăng nhập hay trang 403 của web.
 * Tạo trong ApiSecurityConfig, không phải bean, để không lẫn vào chuỗi bảo mật web hay conversion service của MVC.
 */
@RequiredArgsConstructor
public class ApiErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrors apiErrors;
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, ApiErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ApiErrorCode.FORBIDDEN);
    }

    public void write(HttpServletResponse response, ApiErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), apiErrors.of(code));
    }
}
