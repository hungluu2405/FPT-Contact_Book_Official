package vn.edu.fpt.contactbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import vn.edu.fpt.contactbook.security.SessionExpiredAccessDeniedHandler;
import vn.edu.fpt.contactbook.service.AccountService;

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
                        "/favicon.ico", "/forgot-password", "/reset-password").permitAll()
                // Danh sách và chi tiết người dùng mở cho trưởng bộ môn (phạm vi bộ môn kiểm tra ở UserService);
                // \d+ để /admin/users/new không lọt vào đây. Mọi URL /admin còn lại chỉ ADMIN.
                .requestMatchers(HttpMethod.GET, "/admin/users", "/admin/users/{id:\\d+}")
                    .hasAnyRole(RoleCodes.ADMIN, RoleCodes.HEAD_OF_DEPARTMENT)
                // Môn học, đầu điểm, lớp học phần mở cho trưởng bộ môn; phạm vi bộ môn kiểm tra ở service.
                // Ghi danh (/admin/sections/{id}/enrollments/**) chỉ ADMIN qua @PreAuthorize của EnrollmentController.
                .requestMatchers("/admin/subjects", "/admin/subjects/**", "/admin/sections", "/admin/sections/**")
                    .hasAnyRole(RoleCodes.ADMIN, RoleCodes.HEAD_OF_DEPARTMENT)
                .requestMatchers("/admin/**").hasRole(RoleCodes.ADMIN)
                .requestMatchers("/teacher/**").hasAnyRole(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT)
                .requestMatchers("/student/**").hasRole(RoleCodes.STUDENT)
                .requestMatchers("/parent/**").hasRole(RoleCodes.PARENT)
                // Tin nhắn chỉ cho SV, PH, GV, TBM (ADMIN không dùng); thành viên hội thoại kiểm tra ở MessageService.
                .requestMatchers("/messages", "/messages/**").hasAnyRole(RoleCodes.STUDENT, RoleCodes.PARENT,
                        RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT)
                // Báo cáo: GV, TBM, ADMIN (phạm vi lớp/bộ môn kiểm tra ở ReportScope); báo cáo CLB chỉ ADMIN.
                .requestMatchers("/reports/clubs", "/reports/clubs/**").hasRole(RoleCodes.ADMIN)
                .requestMatchers("/reports", "/reports/**").hasAnyRole(RoleCodes.ADMIN,
                        RoleCodes.HEAD_OF_DEPARTMENT, RoleCodes.TEACHER)
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
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedHandler(new SessionExpiredAccessDeniedHandler())
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
                                                                   LoginAttemptService loginAttemptService,
                                                                   AccountService accountService) {
        return new PhoneAuthenticationProvider(userDetailsService, passwordEncoder, loginAttemptService,
                accountService);
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
