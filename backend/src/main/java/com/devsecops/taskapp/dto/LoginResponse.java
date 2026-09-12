package com.devsecops.taskapp.dto;

public record LoginResponse(String token, String tokenType, UserResponse user) {
}
