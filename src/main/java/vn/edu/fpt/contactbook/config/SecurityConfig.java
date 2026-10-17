package vn.edu.fpt.contactbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.security.LoginAttemptService;
import vn.edu.fpt.contactbook.security.LoginSuccessHandler;
import vn.edu.fpt.contactbook.security.MustChangePasswordFilter;
import vn.edu.fpt.contactbook.security.PhoneAuthenticationProvider;

import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler)
            throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/css/**", "/js/**", "/webjars/**", "/images/**", "/error",
                        "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole(RoleCodes.ADMIN)
                .requestMatchers("/teacher/**").hasAnyRole(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT)
                .requestMatchers("/student/**").hasRole(RoleCodes.STUDENT)
                .requestMatchers("/parent/**").hasRole(RoleCodes.PARENT)
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("phone")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .addFilterAfter(new MustChangePasswordFilter(), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public PhoneAuthenticationProvider phoneAuthenticationProvider(UserDetailsService userDetailsService,
                                                                   PasswordEncoder passwordEncoder,
                                                                   LoginAttemptService loginAttemptService) {
        return new PhoneAuthenticationProvider(userDetailsService, passwordEncoder, loginAttemptService);
    }

    /** Chỉ đưa mã lỗi lên URL; AuthController đổi mã thành thông báo tiếng Việt. */
    private static ExceptionMappingAuthenticationFailureHandler loginFailureHandler() {
        ExceptionMappingAuthenticationFailureHandler handler = new ExceptionMappingAuthenticationFailureHandler();
        handler.setDefaultFailureUrl("/login?error");
        handler.setExceptionMappings(Map.of(
                LockedException.class.getName(), "/login?error=locked",
                DisabledException.class.getName(), "/login?error=disabled"));
        return handler;
    }
}
