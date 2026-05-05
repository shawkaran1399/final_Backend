package com.buildledger.vendor.config;

import com.buildledger.vendor.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    res.getWriter().write(
                        "{\"success\":false,\"message\":\"Authentication required. Please provide a valid Bearer token.\"}");
                })
                .accessDeniedHandler((req, res, accessEx) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    res.getWriter().write(
                        "{\"success\":false,\"message\":\"Access denied: insufficient permissions.\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public: vendor self-registration and document upload (vendor has no token yet)
                .requestMatchers(HttpMethod.POST, "/vendors/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/vendors/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/vendors/*/documents").permitAll()
                .requestMatchers(HttpMethod.PUT, "/vendors/*/documents/replace").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

