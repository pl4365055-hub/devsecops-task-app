package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import com.devsecops.taskapp.dto.UserResponse;
import com.devsecops.taskapp.entity.User;
import com.devsecops.taskapp.security.JwtService;
import com.devsecops.taskapp.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * uat / test 環境的資料庫登入：
 * 從 users 表查詢使用者，以 BCrypt 驗證密碼，成功後簽發應用自己的 JWT。
 */
@Service
@Profile({"uat", "test"})
public class DbAuthenticationService implements AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public DbAuthenticationService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("使用者名稱或密碼錯誤");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, "Bearer", UserResponse.from(user));
    }
}
