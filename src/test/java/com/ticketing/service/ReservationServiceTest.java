package com.ticketing.service;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.dto.reservation.ReservationResponse;
import com.ticketing.entity.Event;
import com.ticketing.entity.Reservation;
import com.ticketing.entity.ReservationStatus;
import com.ticketing.entity.Role;
import com.ticketing.exception.ApiException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long EVENT_ID = 1L;
    private static final String KEY = "key-1";

    private final AuthUser customer = new AuthUser(3L, "customer@test.local", Set.of(Role.CUSTOMER));
    private final AuthUser otherCustomer = new AuthUser(4L, "other@test.local", Set.of(Role.CUSTOMER));

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EventService eventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void createRejectsWhenNotEnoughSeatsLeft() {
        givenEvent(event(5, futureStart()));
        when(reservationRepository.totalEventReservation(EVENT_ID)).thenReturn(4L);

        assertThatThrownBy(() -> reservationService.create(EVENT_ID, new ReservationRequest(2), KEY, customer))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createAcceptsWhenRequestedSeatsEqualRemainingSeats() {
        givenEvent(event(5, futureStart()));
        when(reservationRepository.totalEventReservation(EVENT_ID)).thenReturn(3L);

        ReservationResponse response = reservationService.create(EVENT_ID, new ReservationRequest(2), KEY, customer);

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.seats()).isEqualTo(2);
        assertThat(response.userId()).isEqualTo(customer.id());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void createRejectsWhenEventHasStarted() {
        givenEvent(event(5, Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThatThrownBy(() -> reservationService.create(EVENT_ID, new ReservationRequest(1), KEY, customer))
                .isInstanceOf(ApiException.class)
                .hasMessage("Event has already started");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReturnsExistingReservationForRepeatedKey() {
        givenEvent(event(5, futureStart()));
        when(idempotencyService.resolveExistingResourceId(anyString(), anyString(), any(), anyString()))
                .thenReturn(Optional.of(7L));
        when(reservationRepository.findById(7L)).thenReturn(Optional.of(reservation(7L, customer.id(), ReservationStatus.PENDING)));

        ReservationResponse response = reservationService.create(EVENT_ID, new ReservationRequest(1), KEY, customer);

        assertThat(response.id()).isEqualTo(7L);
        verify(idempotencyService, never()).start(anyString(), anyString(), any(), anyString());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmChangesPendingToConfirmed() {
        when(reservationRepository.findById(10L))
                .thenReturn(Optional.of(reservation(10L, customer.id(), ReservationStatus.PENDING)));
        when(eventService.findEventById(EVENT_ID)).thenReturn(event(5, futureStart()));

        assertThat(reservationService.confirm(10L, customer).status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void otherCustomerCannotChangeReservation() {
        when(reservationRepository.findById(10L))
                .thenReturn(Optional.of(reservation(10L, customer.id(), ReservationStatus.PENDING)));

        assertThatThrownBy(() -> reservationService.confirm(10L, otherCustomer))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    private void givenEvent(Event event) {
        when(eventService.findPublishedForUpdate(EVENT_ID)).thenReturn(event);
        when(idempotencyService.resolveExistingResourceId(anyString(), anyString(), any(), anyString()))
                .thenReturn(Optional.empty());
    }

    private Instant futureStart() {
        return Instant.now().plus(10, ChronoUnit.DAYS);
    }

    private Event event(int capacity, Instant startsAt) {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setCapacity(capacity);
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plus(2, ChronoUnit.HOURS));
        event.setPublished(true);
        return event;
    }

    private Reservation reservation(Long id, Long userId, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setEventId(EVENT_ID);
        reservation.setUserId(userId);
        reservation.setStatus(status);
        reservation.setSeats(1);
        return reservation;
    }
}
