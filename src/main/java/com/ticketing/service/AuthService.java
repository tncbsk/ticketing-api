package com.ticketing.service;

import com.ticketing.dto.auth.LoginRequest;
import com.ticketing.dto.auth.RefreshRequest;
import com.ticketing.dto.auth.RegisterRequest;
import com.ticketing.dto.auth.TokenResponse;
import com.ticketing.dto.auth.UserResponse;
import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.UserRepository;
import com.ticketing.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Role role = request.role() != null ? request.role() : Role.CUSTOMER;
        if (role == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin users cannot be self-registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(role);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        user.setLastLoginAt(LocalDateTime.now());

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        User user = userRepository.findById(Long.valueOf(claims.getSubject()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        return new TokenResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                jwtService.getAccessTokenSeconds());
    }
}
