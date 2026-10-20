package vn.edu.fpt.contactbook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.fpt.contactbook.security.JwtTokenService;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties properties, Clock clock) {
        return new JwtTokenService(JwtTokenService.signingKey(properties.secret()), properties.accessTokenTtl(),
                properties.refreshTokenTtl(), clock);
    }
}
