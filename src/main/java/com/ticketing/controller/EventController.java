package com.ticketing.controller;

import com.ticketing.config.OpenApiConfig;
import com.ticketing.dto.event.EventRequest;
import com.ticketing.dto.event.EventResponse;
import com.ticketing.security.AuthUser;
import com.ticketing.service.EventService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public EventResponse create(@Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal AuthUser user) {
        return eventService.create(request, user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public EventResponse update(@PathVariable Long id,
                                @Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal AuthUser user) {
        return eventService.update(id, request, user);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public EventResponse publish(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return eventService.publish(id, user);
    }

    @GetMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public List<EventResponse> list(@RequestParam(required = false) Long ownerId, @AuthenticationPrincipal AuthUser user) {
        return eventService.list(ownerId, user);
    }

    @GetMapping("/public")
    public List<EventResponse> searchPublished(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q) {
        return eventService.searchPublished(from, to, q);
    }
}
