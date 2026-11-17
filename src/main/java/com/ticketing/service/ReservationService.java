package com.ticketing.service;

import com.ticketing.util.HashUtils;
import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.dto.reservation.ReservationResponse;
import com.ticketing.entity.*;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventService eventService;
    private final IdempotencyService idempotencyService;

    @Transactional
    public ReservationResponse create(Long eventId, ReservationRequest request, String idempotencyKey, AuthUser user) {
        final String endpoint = "POST /api/events/" + eventId + "/reservations";
        final String requestHash = HashUtils.sha256("seats=" + request.seats());
        final Optional<IdempotencyKey> optionalIdempotencyKey = idempotencyService.findActive(idempotencyKey, endpoint, user.id());
        if (optionalIdempotencyKey.isPresent()) {
            final IdempotencyKey existingIdempotency = optionalIdempotencyKey.get();
            if (!existingIdempotency.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key was already used with a different request");
            }
            return reservationRepository.findById(existingIdempotency.getResourceId())
                    .map(ReservationResponse::from)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found for idempotency key"));
        }
        IdempotencyKey newIdempotencyKey = new IdempotencyKey();
        newIdempotencyKey.setKey(idempotencyKey);
        newIdempotencyKey.setTtl(IdempotencyService.IDEMPOTENCY_TTL_SECONDS);
        newIdempotencyKey.setUserId(user.id());
        newIdempotencyKey.setEndpoint(endpoint);
        newIdempotencyKey.setRequestHash(requestHash);
        idempotencyService.saveIdempotency(newIdempotencyKey);

        final Event event = eventService.findPublishedForUpdate(eventId);

        if (event.hasStarted(LocalDateTime.now())) {
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
        final String responseHash = HashUtils.sha256(response.toString());

        newIdempotencyKey.setResponseHash(responseHash);
        newIdempotencyKey.setResourceId(reservation.getId());
        newIdempotencyKey.setStatus(IdempotencyStatus.COMPLETED);
        idempotencyService.saveIdempotency(newIdempotencyKey);

        return response;
    }

    public ReservationResponse confirm(Long reservationId, AuthUser user) {

        final Reservation reservation = findReservationAndCheckOwner(reservationId, user);

        if (!ReservationStatus.PENDING.equals(reservation.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending reservations can be confirmed");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.saveAndFlush(reservation);
        return ReservationResponse.from(reservation);
    }

    public ReservationResponse cancel(Long reservationId, AuthUser user) {

        final Reservation reservation = findReservationAndCheckOwner(reservationId, user);

        if (ReservationStatus.CANCELLED.equals(reservation.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Reservation is already cancelled");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.saveAndFlush(reservation);
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
        if (event.hasStarted(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Reservation cannot be changed after the event has started");
        }

        return reservation;
    }
}
