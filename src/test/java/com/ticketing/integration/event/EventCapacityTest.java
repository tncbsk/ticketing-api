package com.ticketing.integration.event;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.EventRepository;
import com.ticketing.repository.ReservationRepository;
import com.ticketing.security.AuthUser;
import com.ticketing.service.ReservationService;
import com.ticketing.support.Concurrently;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCapacityTest extends IntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void capacityCannotBeLoweredBelowReservedSeats() {
        Long eventId = createPublishedEvent(5);
        reservationService.create(eventId, new ReservationRequest(4), "cap-low-" + eventId, customer());

        assertThatThrownBy(() -> eventService.update(eventId, eventRequest(2), organizer()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
        assertThat(eventRepository.findById(eventId).orElseThrow().getCapacity()).isEqualTo(5);
    }

    @Test
    void loweringCapacityWhileReservationsComeInNeverOverbooks() throws Exception {
        AuthUser customer = customer();
        AuthUser organizer = organizer();

        for (int round = 0; round < 10; round++) {
            Long eventId = createPublishedEvent(10);
            String keyPrefix = "cap-race-" + eventId + "-";

            Concurrently.run(10, i -> {
                if (i == 0) {
                    eventService.update(eventId, eventRequest(5), organizer);
                } else {
                    reservationService.create(eventId, new ReservationRequest(1), keyPrefix + i, customer);
                }
            });

            int capacity = eventRepository.findById(eventId).orElseThrow().getCapacity();
            assertThat(reservationRepository.totalEventReservation(eventId)).isLessThanOrEqualTo(capacity);
        }
    }
}
