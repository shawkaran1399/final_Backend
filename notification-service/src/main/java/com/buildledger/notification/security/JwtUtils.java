package com.buildledger.notification.security;

import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;

@Component @Slf4j
public class JwtUtils {
    @Value("${jwt.secret}") private String jwtSecret;
    public boolean validateToken(String t) {
        try { Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(t); return true; }
        catch (JwtException | IllegalArgumentException e) { log.warn("Invalid JWT: {}", e.getMessage()); return false; }
    }
    public String extractUsername(String t) { return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(t).getBody().getSubject(); }
    public String extractRole(String t) { return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(t).getBody().get("role", String.class); }
    private Key key() { return Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}

