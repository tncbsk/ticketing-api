package com.ticketing.service;

import com.ticketing.dto.event.EventRequest;
import com.ticketing.entity.Event;
import com.ticketing.entity.Role;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Long EVENT_ID = 1L;

    private final AuthUser owner = new AuthUser(2L, "organizer@test.local", Set.of(Role.ORGANIZER));
    private final AuthUser otherOrganizer = new AuthUser(6L, "other@test.local", Set.of(Role.ORGANIZER));
    private final AuthUser admin = new AuthUser(1L, "admin@test.local", Set.of(Role.ADMIN));

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EventService eventService;

    @Test
    void otherOrganizerCannotUpdateEvent() {
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(owner.id(), 10)));

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request(20), otherOrganizer))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCanUpdateAnyEvent() {
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(owner.id(), 10)));
        when(reservationRepository.totalEventReservation(EVENT_ID)).thenReturn(0L);

        assertThat(eventService.update(EVENT_ID, request(20), admin).capacity()).isEqualTo(20);
    }

    @Test
    void capacityCannotBeLoweredBelowReservedSeats() {
        when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(owner.id(), 10)));
        when(reservationRepository.totalEventReservation(EVENT_ID)).thenReturn(4L);

        assertThatThrownBy(() -> eventService.update(EVENT_ID, request(2), owner))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    private Event event(Long ownerId, int capacity) {
        Instant startsAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setOwnerId(ownerId);
        event.setTitle("Concert");
        event.setVenue("Hall");
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plus(2, ChronoUnit.HOURS));
        event.setCapacity(capacity);
        event.setPublished(true);
        return event;
    }

    private EventRequest request(int capacity) {
        Instant startsAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return new EventRequest("Concert", "Hall", startsAt, startsAt.plus(2, ChronoUnit.HOURS), capacity);
    }
}
