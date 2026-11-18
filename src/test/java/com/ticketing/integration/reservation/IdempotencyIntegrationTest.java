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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIntegrationTest extends IntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void sameKeyReturnsTheSameReservation() {
        Long eventId = createPublishedEvent(10);
        String key = "repeat-" + eventId;

        Long first = reservationService.create(eventId, new ReservationRequest(2), key, customer()).id();
        Long second = reservationService.create(eventId, new ReservationRequest(2), key, customer()).id();

        assertThat(second).isEqualTo(first);
        assertThat(reservationRepository.findByEventId(eventId)).hasSize(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        Long eventId = createPublishedEvent(10);
        String key = "mismatch-" + eventId;
        reservationService.create(eventId, new ReservationRequest(2), key, customer());

        assertThatThrownBy(() -> reservationService.create(eventId, new ReservationRequest(5), key, customer()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
        assertThat(reservationRepository.findByEventId(eventId)).hasSize(1);
    }

    @Test
    void parallelRequestsWithSameKeyCreateOneReservation() throws Exception {
        Long eventId = createPublishedEvent(100);
        String key = "parallel-" + eventId;
        AuthUser customer = customer();
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        List<Throwable> errors = Concurrently.run(10, i ->
                ids.add(reservationService.create(eventId, new ReservationRequest(1), key, customer).id()));

        assertThat(errors).isEmpty();
        assertThat(ids).hasSize(1);
        assertThat(reservationRepository.findByEventId(eventId)).hasSize(1);
    }

    @Test
    void tooLongKeyIsRejectedWithBadRequest() throws Exception {
        Long eventId = createPublishedEvent(10);

        mockMvc.perform(post("/api/events/" + eventId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL))
                        .header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key must be between 1 and 100 characters"));
    }
}
