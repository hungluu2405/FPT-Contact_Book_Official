package vn.edu.fpt.contactbook.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu OpenAPI (/v3/api-docs, /swagger-ui.html) làm hợp đồng cho mobile. Chỉ mô tả /api/** (application.properties).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI contactBookOpenApi() {
        return new OpenAPI()
                .info(new Info().title("FPT Contact Book API").version("v1")
                        .description("REST API cho app mobile Sổ liên lạc điện tử. Đăng nhập lấy access token, "
                                + "gửi kèm header Authorization: Bearer <token>."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
