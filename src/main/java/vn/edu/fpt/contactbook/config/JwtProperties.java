package vn.edu.fpt.contactbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình JWT của REST API (app.jwt.*). secret lấy từ biến môi trường JWT_SECRET; để trống thì ứng dụng tự sinh
 * khóa ngẫu nhiên mỗi lần khởi động (chỉ hợp với máy dev, xem docs/ASSUMPTIONS.md).
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("14d") Duration refreshTokenTtl) {
}
