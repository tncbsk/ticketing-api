package com.ticketing.service;

import com.ticketing.dto.auth.LoginRequest;
import com.ticketing.dto.auth.RefreshRequest;
import com.ticketing.dto.auth.RegisterRequest;
import com.ticketing.entity.AuditAction;
import com.ticketing.entity.ResourceType;
import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.UserRepository;
import com.ticketing.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void adminCannotSelfRegister() {
        RegisterRequest request = new RegisterRequest("new@test.local", "Passw0rd!x", Role.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.existsByEmail("taken@test.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Taken@Test.local", "Passw0rd!x", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshRejectsAccessToken() {
        Claims claims = mock(Claims.class);
        when(jwtService.parse("access-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access-token")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void failedLoginIsAuditedInItsOwnTransactionAndRethrown() {
        User user = new User();
        user.setId(5L);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        when(userRepository.findByEmail("customer@test.local")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("customer@test.local", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verify(auditService).recordFailure(null, AuditAction.LOGIN_FAILED, ResourceType.USER, 5L);
        verify(auditService, never()).record(any(), any(), any(), any());
    }
}
