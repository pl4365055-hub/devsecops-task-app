package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;
import com.devsecops.taskapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("!dev")  # 非 dev profile 时激活（uat / prod）
public class EnterpriseAuthenticationService implements AuthenticationService {

    @Value("${enterprise.sso.url}")
    private String ssoUrl;

    @Value("${enterprise.sso.api-key}")
    private String ssoApiKey;

    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    public EnterpriseAuthenticationService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResponse authenticate(LoginRequest request) {
        # 调用企业 SSO 验证
        SsoValidationRequest ssoRequest = new SsoValidationRequest(
            request.getUsername(), request.getPassword()
        );

        try {
            SsoValidationResponse ssoResponse = restTemplate.postForObject(
                ssoUrl + "/validate",
                ssoRequest,
                SsoValidationResponse.class
            );

            if (ssoResponse == null || !ssoResponse.isValid()) {
                throw new RuntimeException("SSO authentication failed");
            }

            # SSO 返回用户信息，签发应用自己的 JWT
            String token = jwtUtil.generateToken(
                ssoResponse.getUsername(),
                ssoResponse.getRole()
            );
            return new LoginResponse(token, ssoResponse.getUsername(), ssoResponse.getRole());

        } catch (Exception e) {
            throw new RuntimeException("SSO service unavailable: " + e.getMessage());
        }
    }

    @Override
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    @Override
    public String getUsernameFromToken(String token) {
        return jwtUtil.parseToken(token).getSubject();
    }

    @Override
    public String getRoleFromToken(String token) {
        return jwtUtil.parseToken(token).get("role", String.class);
    }

    # 内部 DTO
    private record SsoValidationRequest(String username, String password) {}
    private record SsoValidationResponse(boolean valid, String username, String role) {}
}
