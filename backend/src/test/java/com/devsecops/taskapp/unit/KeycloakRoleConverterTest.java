package com.devsecops.taskapp.unit;

import com.devsecops.taskapp.config.KeycloakRoleConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private Jwt jwtWithRoles(List<String> roles) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("realm_access", Map.of("roles", roles))
        );
    }

    @Test
    void convert_shouldMapRealmRolesToAuthorities() {
        var auth = converter.convert(jwtWithRoles(
                List.of("ADMIN", "default-roles-taskapp", "offline_access")));

        Collection<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("ROLE_offline_access"));
        assertEquals(1, authorities.size());
    }
}