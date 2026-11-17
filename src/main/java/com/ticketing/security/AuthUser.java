package com.ticketing.security;

import com.ticketing.entity.Role;

import java.util.Set;


public record AuthUser(Long id, String email, Set<Role> roles) {

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}
