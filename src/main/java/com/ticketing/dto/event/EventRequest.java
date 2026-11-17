package com.ticketing.dto.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Future LocalDateTime startsAt,
        @NotNull @Future LocalDateTime endsAt,
        @NotNull @Positive Integer capacity
) {
}
