package com.ticketing.integration.reservation;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import com.ticketing.service.ReservationService;
import com.ticketing.support.Concurrently;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationConcurrencyTest extends IntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void fiftyParallelRequestsForTenSeatsCreateExactlyTenReservations() throws Exception {
        Long eventId = createPublishedEvent(10);
        AuthUser customer = customer();

        List<Throwable> errors = Concurrently.run(50, i ->
                reservationService.create(eventId, new ReservationRequest(1), "oversell-" + eventId + "-" + i, customer));

        assertThat(errors).hasSize(40).allSatisfy(error -> assertThat(error)
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT));
        assertThat(reservationRepository.findByEventId(eventId)).hasSize(10);
        assertThat(reservationRepository.totalEventReservation(eventId)).isEqualTo(10L);
    }
}
