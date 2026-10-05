package com.devsecops.taskapp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 認證模式發現端點：前端啟動時查詢，決定使用本機表單或 Keycloak SSO。
 * 屬於公開端點（/api/auth/** permitAll）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthConfigController {

    /** local = 帳號密碼表單；sso = Keycloak 跳轉登入 */
    @Value("${app.auth.mode:local}")
    private String authMode;

    @Value("${keycloak.url:}")
    private String keycloakUrl;

    @Value("${keycloak.realm:taskapp}")
    private String realm;

    @Value("${keycloak.client-id:task-app}")
    private String clientId;

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of(
                "mode", authMode,
                "keycloakUrl", keycloakUrl,
                "realm", realm,
                "clientId", clientId
        );
    }
}