package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("dev")  # 仅在 dev profile 激活
public class MockAuthenticationService implements AuthenticationService {

    # 模拟用户库
    private static final Map<String, String> USERS = Map.of(
        "admin", "ADMIN",
        "user", "USER"
    );

    # 模拟 token 存储（生产用 JWT）
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    @Override
    public LoginResponse authenticate(LoginRequest request) {
        String role = USERS.get(request.getUsername());
        if (role == null) {
            throw new RuntimeException("Invalid username");
        }
        # Mock：不验证密码，任何密码都能登录
        String token = "mock-" + UUID.randomUUID();
        tokenStore.put(token, request.getUsername() + ":" + role);
        return new LoginResponse(token, request.getUsername(), role);
    }

    @Override
    public boolean validateToken(String token) {
        return tokenStore.containsKey(token);
    }

    @Override
    public String getUsernameFromToken(String token) {
        String info = tokenStore.get(token);
        return info != null ? info.split(":")[0] : null;
    }

    @Override
    public String getRoleFromToken(String token) {
        String info = tokenStore.get(token);
        return info != null ? info.split(":")[1] : null;
    }
}
