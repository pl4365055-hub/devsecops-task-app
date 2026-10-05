package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import com.devsecops.taskapp.dto.UserResponse;
import com.devsecops.taskapp.security.JwtService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * dev 環境的 Mock 登入：
 * - 不連資料庫、不檢查密碼
 * - 只要使用者名稱存在即可登入
 * - 登入後仍簽發標準 JWT，下游安全鏈與其他環境一致
 */
@Service
@Profile("dev")
public class MockAuthenticationService implements AuthenticationService {

    private static final Map<String, String> MOCK_USERS = Map.of(
            "admin", "ADMIN",
            "user", "USER"
    );

    private final JwtService jwtService;

    public MockAuthenticationService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String role = MOCK_USERS.get(request.username());
        if (role == null) {
            throw new BadCredentialsException("Mock 使用者不存在，可用：admin / user");
        }
        // Mock：不驗證密碼
        String token = jwtService.generateToken(request.username(), role);
        UserResponse user = new UserResponse(null, request.username(), role, null);
        return new LoginResponse(token, "Bearer", user);
    }
}
