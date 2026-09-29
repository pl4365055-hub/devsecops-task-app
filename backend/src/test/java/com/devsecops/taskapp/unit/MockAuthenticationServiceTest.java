package com.devsecops.taskapp.unit;

import com.devsecops.taskapp.auth.MockAuthenticationService;
import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import com.devsecops.taskapp.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockAuthenticationServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-that-is-long-enough-123456",
            60_000
    );

    private final MockAuthenticationService mockAuth =
            new MockAuthenticationService(jwtService);

    @Test
    void login_shouldIgnorePasswordAndReturnRole() {
        LoginResponse response = mockAuth.login(
                new LoginRequest("admin", "anything"));

        assertNotNull(response.token());
        assertEquals("admin", response.user().username());
        assertEquals("ADMIN", response.user().role());
    }

    @Test
    void login_unknownUser_shouldThrow() {
        assertThrows(RuntimeException.class,
                () -> mockAuth.login(new LoginRequest("ghost", "x")));
    }
}