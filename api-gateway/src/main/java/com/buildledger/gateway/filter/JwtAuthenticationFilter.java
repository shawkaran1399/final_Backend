package com.buildledger.gateway.filter;

import com.buildledger.gateway.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global JWT Authentication Filter.
 * Validates the JWT token for all secured routes and forwards user identity
 * as headers (X-User-Id, X-Username, X-User-Role) to downstream services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // Paths that do NOT require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/vendors/register",
            "/api/vendors/auth/login",           // pending vendor login
            "/api/vendors/*/documents",        // vendor doc upload (POST) – public
            "/api/vendors/*/documents/replace" // vendor doc replace (PUT) – public
    );

    // Read-only GET paths that are open to all
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/vendors/**",
            "/api/contracts/**",
            "/api/projects/**",
            "/api/deliveries/**",
            "/api/services/**",
            "/api/invoices/**",
            "/api/payments/**",
            "/api/compliance/**",
            "/api/audits/**",
            "/api/users/**",
            "/api/notifications/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        log.debug("Gateway filter: {} {}", method, path);

        // Allow Swagger/actuator paths
        if (isSwaggerPath(path) || isActuatorPath(path)) {
            return chain.filter(exchange);
        }

        // Allow public paths (POST to vendor register, login, doc upload)
        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        // Check Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Validate JWT
        if (!jwtUtil.validateToken(token)) {
            return unauthorizedResponse(exchange, "Invalid or expired JWT token");
        }

        // Extract user info and forward as headers to downstream services
        try {
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);
            Long userId = jwtUtil.extractUserId(token);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId != null ? userId.toString() : "")
                    .header("X-Username", username != null ? username : "")
                    .header("X-User-Role", role != null ? role : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.error("Error extracting JWT claims: {}", e.getMessage());
            return unauthorizedResponse(exchange, "Token processing error");
        }
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        // Login and vendor register are always public
        if (path.equals("/api/auth/login")) return true;
        if (path.equals("/api/vendors/register")) return true;
        if (path.equals("/api/vendors/auth/login")) return true;

        // POST and PUT to vendor documents (upload/replace) are public
        if (path.matches("/api/vendors/\\d+/documents") &&
                (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method))) {
            return true;
        }

        // GET requests to common read paths are public
        if (HttpMethod.GET.equals(method)) {
            for (String pattern : PUBLIC_GET_PATHS) {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSwaggerPath(String path) {
        return path.contains("/swagger-ui") || path.contains("/v3/api-docs") ||
                path.contains("/swagger-resources") || path.contains("/webjars");
    }

    private boolean isActuatorPath(String path) {
        return path.startsWith("/actuator");
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // Highest priority
    }
}