package com.ticketing.service;

import com.ticketing.dto.event.EventRequest;
import com.ticketing.dto.event.EventResponse;
import com.ticketing.entity.AuditAction;
import com.ticketing.entity.Event;
import com.ticketing.entity.ResourceType;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final AuditService auditService;
    private final ReservationRepository reservationRepository;

    @Transactional
    public EventResponse create(EventRequest eventRequest, AuthUser user) {

        validateEventDates(eventRequest);
        Event event = new Event();
        event.setCapacity(eventRequest.capacity());
        event.setTitle(eventRequest.title());
        event.setVenue(eventRequest.venue());
        event.setOwnerId(user.id());
        event.setStartsAt(eventRequest.startsAt());
        event.setEndsAt(eventRequest.endsAt());
        event.setPublished(false);
        eventRepository.save(event);
        auditService.record(user.id(), AuditAction.EVENT_CREATED, ResourceType.EVENT, event.getId());
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse update(Long eventId, EventRequest eventRequest, AuthUser user) {

        // same row lock as reservation creation, so capacity check and new reservations cannot interleave
        final Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event not found"));

        checkOwner(event, user);
        validateEventDates(eventRequest);
        validateCapacity(eventId, eventRequest.capacity());
        event.setCapacity(eventRequest.capacity());
        event.setTitle(eventRequest.title());
        event.setVenue(eventRequest.venue());
        event.setStartsAt(eventRequest.startsAt());
        event.setEndsAt(eventRequest.endsAt());
        eventRepository.saveAndFlush(event);
        auditService.record(user.id(), AuditAction.EVENT_UPDATED, ResourceType.EVENT, event.getId());
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse publish(Long id, AuthUser user) {
        final Event event = findEventAndCheckOwner(id, user);
        event.setPublished(true);
        eventRepository.saveAndFlush(event);
        auditService.record(user.id(), AuditAction.EVENT_PUBLISHED, ResourceType.EVENT, event.getId());
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(Long ownerId, AuthUser user) {

        if (!user.isAdmin()) {
            if (ownerId != null && !user.id().equals(ownerId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only list your own events");
            }
            ownerId = user.id();
        }

        List<Event> events = ownerId == null
                ? eventRepository.findAll()
                : eventRepository.findByOwnerId(ownerId);

        return events.stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> searchPublished(Instant from, Instant to, String q) {
        final List<Event> eventList = eventRepository.searchPublishedEventWithParams(from, to, q);

        return eventList.stream().map(EventResponse::from).toList();
    }

    public Event findPublishedForUpdate(Long eventId) {
        return eventRepository.findPublishedForUpdate(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public Event findEventById(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    private void validateEventDates(EventRequest eventRequest) {

        if (!eventRequest.endsAt().isAfter(eventRequest.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
    }

    private void validateCapacity(Long eventId, int newCapacity) {
        final long reservedSeats = reservationRepository.totalEventReservation(eventId);
        if (reservedSeats > newCapacity) {
            throw new ApiException(HttpStatus.CONFLICT, "Capacity cannot be lower than the number of reserved seats");
        }
    }

    private Event findEventAndCheckOwner(Long eventId, AuthUser user) {
        final Event event = findEventById(eventId);
        checkOwner(event, user);
        return event;
    }

    private void checkOwner(Event event, AuthUser user) {
        if (!event.getOwnerId().equals(user.id()) && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only event owner or admin can modify the event");
        }
    }
}
