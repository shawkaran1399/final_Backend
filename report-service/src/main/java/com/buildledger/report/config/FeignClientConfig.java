package com.buildledger.report.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    /**
     * Forwards the Authorization (JWT) token and X-User-Role / X-Username headers
     * from the incoming HTTP request to all outgoing Feign calls.
     */
    @Bean
    public RequestInterceptor forwardAuthHeaderInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;

            HttpServletRequest request = attrs.getRequest();

            // Forward JWT Bearer token
            String auth = request.getHeader("Authorization");
            if (StringUtils.hasText(auth)) {
                requestTemplate.header("Authorization", auth);
            }

            // Forward gateway headers (X-Username, X-User-Role)
            String username = request.getHeader("X-Username");
            String role = request.getHeader("X-User-Role");
            if (StringUtils.hasText(username)) requestTemplate.header("X-Username", username);
            if (StringUtils.hasText(role))     requestTemplate.header("X-User-Role", role);
        };
    }
}

