package com.ticketing.dto.event;

import com.ticketing.entity.Event;

import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        Long ownerId,
        String title,
        String venue,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer capacity,
        boolean published,
        Long version
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getOwnerId(),
                event.getTitle(),
                event.getVenue(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCapacity(),
                event.isPublished(),
                event.getVersion());
    }
}
