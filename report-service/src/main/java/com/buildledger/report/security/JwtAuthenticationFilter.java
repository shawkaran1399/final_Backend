package com.buildledger.report.security;

import jakarta.servlet.*; import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException; import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            String role = req.getHeader("X-User-Role"), username = req.getHeader("X-Username");

            // Option 1: Headers from API Gateway
            if (StringUtils.hasText(role) && StringUtils.hasText(username)) {
                var auth = new UsernamePasswordAuthenticationToken(username, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // Option 2: Parse JWT token directly from Authorization header
                String bearerToken = req.getHeader("Authorization");
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                    String token = bearerToken.substring(7);
                    if (jwtUtils.validateToken(token)) {
                        String extractedUsername = jwtUtils.extractUsername(token);
                        String extractedRole = jwtUtils.extractRole(token);
                        if (StringUtils.hasText(extractedUsername) && StringUtils.hasText(extractedRole)) {
                            var auth = new UsernamePasswordAuthenticationToken(extractedUsername, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + extractedRole)));
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            }
        } catch (Exception e) { log.error("Auth error: {}", e.getMessage()); }
        chain.doFilter(req, res);
    }
}
