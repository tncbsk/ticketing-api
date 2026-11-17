package com.ticketing.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationRequest(@NotNull @Positive Integer seats) {
}
