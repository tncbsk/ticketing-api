package com.ticketing.dto.reservation;

import com.ticketing.entity.Reservation;
import com.ticketing.entity.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long eventId,
        Long userId,
        ReservationStatus status,
        Integer seats,
        Instant createdAt
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEventId(),
                reservation.getUserId(),
                reservation.getStatus(),
                reservation.getSeats(),
                reservation.getCreatedAt());
    }
}
