package vn.edu.fpt.contactbook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.fpt.contactbook.security.JwtTokenService;
import vn.edu.fpt.contactbook.security.PasswordResetCodes;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties, Clock clock) {
        return new JwtTokenService(JwtTokenService.signingKey(properties.secret()), properties.accessTokenTtl(),
                properties.refreshTokenTtl(), clock);
    }

    /** Mã quên mật khẩu ký bằng cùng app.jwt.secret (để trống: khóa ngẫu nhiên riêng, mất khi khởi động lại). */
    @Bean
    public PasswordResetCodes passwordResetCodes(JwtProperties properties, Clock clock) {
        return new PasswordResetCodes(JwtTokenService.signingKey(properties.secret()), clock);
    }
}
