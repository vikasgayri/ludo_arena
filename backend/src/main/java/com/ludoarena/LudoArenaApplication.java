package com.ludoarena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UserDetailsServiceAutoConfiguration is excluded: authentication is JWT-only (JwtAuthFilter / StompAuthInterceptor), so
 * Spring Boot's default in-memory "user" with a generated password must not exist.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class LudoArenaApplication {
    public static void main(String[] args) {
        SpringApplication.run(LudoArenaApplication.class, args);
    }
}
