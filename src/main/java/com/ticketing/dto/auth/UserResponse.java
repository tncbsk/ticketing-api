package com.ticketing.dto.auth;

import com.ticketing.entity.Role;
import com.ticketing.entity.User;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        Set<Role> roles,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRoles(), user.getCreatedAt());
    }
}
