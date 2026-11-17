package com.ticketing.controller;

import com.ticketing.config.OpenApiConfig;
import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.dto.reservation.ReservationResponse;
import com.ticketing.security.AuthUser;
import com.ticketing.service.ReservationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Reservations")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ReservationController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final ReservationService reservationService;

    @PostMapping("/events/{id}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ReservationResponse create(@PathVariable("id") Long eventId,
                                      @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                      @Valid @RequestBody ReservationRequest request,
                                      @AuthenticationPrincipal AuthUser user) {
        return reservationService.create(eventId, request, idempotencyKey, user);
    }

    @PostMapping("/reservations/{id}/confirm")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ReservationResponse confirm(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return reservationService.confirm(id, user);
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ReservationResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return reservationService.cancel(id, user);
    }
}
