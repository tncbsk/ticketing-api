package com.ticketing.security;

import com.ticketing.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final long WINDOW_MILLIS = 60_000;

    private final boolean enabled;
    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.enabled}") boolean enabled,
                           @Value("${app.rate-limit.auth.requests-per-minute}") int requestsPerMinute,
                           ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        long windowStart = now - now % WINDOW_MILLIS;

        Window window = windows.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || current.start() != windowStart
                        ? new Window(windowStart, 1)
                        : new Window(windowStart, current.count() + 1));

        if (window.count() > requestsPerMinute) {
            long retryAfterSeconds = (windowStart + WINDOW_MILLIS - now) / 1000 + 1;
            reject(request, response, retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                "Too many requests, please try again later", request.getRequestURI());

        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private record Window(long start, int count) {
    }
}
