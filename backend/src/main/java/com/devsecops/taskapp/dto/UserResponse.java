package com.devsecops.taskapp.dto;

import com.devsecops.taskapp.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String username, String role, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
