package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;

/**
 * 帳號密碼登入服務（dev / uat / test 使用）。
 * prod 使用 Keycloak SSO，不提供此實作。
 */
public interface AuthenticationService {
    LoginResponse login(LoginRequest request);
}
