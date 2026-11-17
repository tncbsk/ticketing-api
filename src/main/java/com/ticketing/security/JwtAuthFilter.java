package com.ticketing.security;

import com.ticketing.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(BEARER.length()));
            if (jwtService.isAccessToken(claims)) {
                authenticate(claims);
            }
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        List<?> roleNames = claims.get("roles", List.class);
        Set<Role> roles = roleNames.stream()
                .map(r -> Role.valueOf(r.toString()))
                .collect(Collectors.toSet());

        AuthUser principal = new AuthUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class), roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
