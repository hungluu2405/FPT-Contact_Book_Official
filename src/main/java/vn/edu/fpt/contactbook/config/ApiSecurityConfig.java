package vn.edu.fpt.contactbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.exception.ApiErrors;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.ApiErrorWriter;
import vn.edu.fpt.contactbook.security.ApiMustChangePasswordFilter;
import vn.edu.fpt.contactbook.security.ApiPrincipalConverter;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.security.JwtTokenService;

/**
 * Chuỗi bảo mật của REST API cho mobile, xét trước chuỗi web (SecurityConfig, không đặt @Order nên xét sau cùng).
 * Stateless, xác thực bằng access token trong header Authorization: Bearer, không cookie phiên, nên tắt CSRF ở
 * đây là an toàn (ngoại lệ duy nhất, CLAUDE.md mục 5). Tài liệu OpenAPI cũng đi qua chuỗi này để mở công khai.
 */
@Configuration
public class ApiSecurityConfig {

    static final String[] OPENAPI_PATHS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService,
                                                      AppUserDetailsService userDetailsService,
                                                      ApiErrors apiErrors, JsonMapper jsonMapper) throws Exception {
        ApiErrorWriter errorWriter = new ApiErrorWriter(apiErrors, jsonMapper);
        ApiPrincipalConverter principalConverter = new ApiPrincipalConverter(userDetailsService);

        http
            .securityMatcher(matchers())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(OPENAPI_PATHS).permitAll()
                // Phạm vi chi tiết (con đã liên kết, dữ liệu của chính mình) kiểm tra lại ở service.
                .requestMatchers("/api/v1/student/**").hasRole(RoleCodes.STUDENT)
                .requestMatchers("/api/v1/parent/**").hasRole(RoleCodes.PARENT)
                .requestMatchers("/api/v1/teacher/**").hasAnyRole(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT)
                .anyRequest().authenticated()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt
                    .decoder(jwtTokenService.accessDecoder())
                    .jwtAuthenticationConverter(principalConverter))
                .authenticationEntryPoint(errorWriter)
                .accessDeniedHandler(errorWriter)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(errorWriter)
                .accessDeniedHandler(errorWriter)
            )
            .addFilterAfter(new ApiMustChangePasswordFilter(errorWriter), AuthorizationFilter.class);

        return http.build();
    }

    private static String[] matchers() {
        String[] matchers = new String[OPENAPI_PATHS.length + 1];
        matchers[0] = "/api/**";
        System.arraycopy(OPENAPI_PATHS, 0, matchers, 1, OPENAPI_PATHS.length);
        return matchers;
    }
}
