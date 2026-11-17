package com.ticketing.service;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.dto.reservation.ReservationResponse;
import com.ticketing.entity.Event;
import com.ticketing.entity.Reservation;
import com.ticketing.entity.ReservationStatus;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.IdempotencyKeyRepository;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final EventService eventService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional
    public ReservationResponse create(Long eventId, ReservationRequest request, String idempotencyKey, AuthUser user) {

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

        return ReservationResponse.from(reservation);
    }

    private void checkSeatCapacity(Long eventId, ReservationRequest request, Event event) {
        final Long totalEventReservation = reservationRepository.totalEventReservation(eventId);
        final int numberOfRemainingSeat = event.getCapacity() - totalEventReservation.intValue();
        final int requestedSeats = request.seats();
        if (requestedSeats > numberOfRemainingSeat) {
            throw new ApiException(HttpStatus.CONFLICT, "Not enough seats available");
        }
    }

    public ReservationResponse confirm(Long id, AuthUser user) {
        // TODO
        return null;
    }

    public ReservationResponse cancel(Long id, AuthUser user) {
        // TODO
        return null;
    }
}
