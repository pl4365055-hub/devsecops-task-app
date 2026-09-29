package com.devsecops.taskapp.auth;

import com.devsecops.taskapp.dto.LoginRequest;
import com.devsecops.taskapp.dto.LoginResponse;

public interface AuthenticationService {
    LoginResponse authenticate(LoginRequest request);
    boolean validateToken(String token);
    String getUsernameFromToken(String token);
    String getRoleFromToken(String token);
}
