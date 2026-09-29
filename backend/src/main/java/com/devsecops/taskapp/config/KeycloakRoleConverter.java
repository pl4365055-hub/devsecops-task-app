package com.devsecops.taskapp.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 Keycloak JWT 中的 realm 角色（realm_access.roles）
 * 轉換成 Spring Security 的 ROLE_* 授權。
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Keycloak 內建角色，不應進入應用授權 */
    private static final Set<String> BUILTIN_ROLES =
            Set.of("default-roles-taskapp", "offline_access", "uma_authorization");

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof List<?> roleList)) {
            return Collections.emptyList();
        }
        return roleList.stream()
                .map(String::valueOf)
                .filter(role -> !BUILTIN_ROLES.contains(role))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}