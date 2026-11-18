package com.ticketing.integration.security;

import com.ticketing.dto.reservation.ReservationRequest;
import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.service.ReservationService;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        User customer = userRepository.findByEmail(CUSTOMER_EMAIL).orElseThrow();

        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateRefreshToken(customer)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(10)))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotReserveSeats() throws Exception {
        Long eventId = createPublishedEvent(10);

        mockMvc.perform(post("/api/events/" + eventId + "/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_EMAIL))
                        .header("Idempotency-Key", "organizer-" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotUpdateAnotherOrganizersEvent() throws Exception {
        Long eventId = createPublishedEvent(10);
        User otherOrganizer = createUser(Role.ORGANIZER);

        mockMvc.perform(put("/api/events/" + eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOrganizer.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(20)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotConfirmAnotherCustomersReservation() throws Exception {
        Long eventId = createPublishedEvent(10);
        Long reservationId = reservationService.create(eventId, new ReservationRequest(1), "owner-" + eventId, customer()).id();
        User otherCustomer = createUser(Role.CUSTOMER);

        mockMvc.perform(post("/api/reservations/" + reservationId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomer.getEmail())))
                .andExpect(status().isForbidden());
    }
}
