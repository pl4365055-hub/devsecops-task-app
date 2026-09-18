package com.devsecops.taskapp.unit;

import com.devsecops.taskapp.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthServiceTest {

    @Test
    void generateToken_shouldContainUsernameAndRole() {
        JwtService jwtService = new JwtService(
                "test-secret-key-that-is-long-enough-123456",
                60_000
        );

        String token = jwtService.generateToken("user", "USER");
        Claims claims = jwtService.parseToken(token);

        assertEquals("user", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
    }
}
