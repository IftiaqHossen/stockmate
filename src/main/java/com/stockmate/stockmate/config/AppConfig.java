package com.stockmate.stockmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * General application-level Spring configuration.
 * Security-specific beans (SecurityFilterChain, etc.) live in SecurityConfig.
 * Keep this class small — only beans that don't belong anywhere else.
 */
@Configuration
public class AppConfig {

    /**
     * BCrypt password encoder — used by UserServiceImpl at registration.
     * Declared here (not in SecurityConfig) to keep security configuration focused
     * on HTTP authorization and authentication flow wiring.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
