package com.ticketing.dto.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EventRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Future Instant startsAt,
        @NotNull @Future Instant endsAt,
        @NotNull @Positive Integer capacity
) {
}
