package com.ticketing.service;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.dto.reservation.ReservationResponse;
import com.ticketing.entity.AuditAction;
import com.ticketing.entity.Event;
import com.ticketing.entity.IdempotencyKey;
import com.ticketing.entity.Reservation;
import com.ticketing.entity.ReservationStatus;
import com.ticketing.entity.ResourceType;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventService eventService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @Transactional
    public ReservationResponse create(Long eventId, ReservationRequest request, String idempotencyKey, AuthUser user) {
        final String endpoint = "POST /api/events/" + eventId + "/reservations";
        final String requestBody = "seats=" + request.seats();
        // lock first so that concurrent requests with the same idempotency key wait for each other.
        final Event event = eventService.findPublishedForUpdate(eventId);

        final Optional<Long> existingReservationId =
                idempotencyService.findCompletedResourceId(idempotencyKey, endpoint, user.id(), requestBody);
        if (existingReservationId.isPresent()) {
            return reservationRepository.findById(existingReservationId.get())
                    .map(ReservationResponse::from)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found for idempotency key"));
        }

        final IdempotencyKey keyRecord = idempotencyService.start(idempotencyKey, endpoint, user.id(), requestBody);

        if (event.hasStarted(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Event has already started");
        }

        checkSeatCapacity(eventId, request, event);

        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setSeats(request.seats());
        reservation.setEventId(eventId);
        reservation.setUserId(user.id());
        reservationRepository.save(reservation);

        final ReservationResponse response = ReservationResponse.from(reservation);
        idempotencyService.complete(keyRecord, reservation.getId(), response.toString());
        auditService.record(user.id(), AuditAction.RESERVATION_CREATED, ResourceType.RESERVATION, reservation.getId());
        return response;
    }

    @Transactional
    public ReservationResponse confirm(Long reservationId, AuthUser user) {

        final Reservation reservation = findReservationAndCheckOwner(reservationId, user);

        if (!ReservationStatus.PENDING.equals(reservation.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending reservations can be confirmed");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.saveAndFlush(reservation);
        auditService.record(user.id(), AuditAction.RESERVATION_CONFIRMED, ResourceType.RESERVATION, reservation.getId());
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse cancel(Long reservationId, AuthUser user) {

        final Reservation reservation = findReservationAndCheckOwner(reservationId, user);

        if (ReservationStatus.CANCELLED.equals(reservation.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Reservation is already cancelled");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.saveAndFlush(reservation);
        auditService.record(user.id(), AuditAction.RESERVATION_CANCELLED, ResourceType.RESERVATION, reservation.getId());
        return ReservationResponse.from(reservation);
    }

    private void checkSeatCapacity(Long eventId, ReservationRequest request, Event event) {

        final Long totalEventReservation = reservationRepository.totalEventReservation(eventId);
        final int requestedSeats = request.seats();
        final int numberOfRemainingSeat = event.getCapacity() - totalEventReservation.intValue();

        if (requestedSeats > numberOfRemainingSeat) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough seats available");
        }
    }

    private Reservation findReservationAndCheckOwner(Long reservationId, AuthUser user) {
        final Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation not found"));

        if (!user.id().equals(reservation.getUserId()) && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only modify your own reservations");
        }

        final Event event = eventService.findEventById(reservation.getEventId());
        if (event.hasStarted(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Reservation cannot be changed after the event has started");
        }

        return reservation;
    }
}
