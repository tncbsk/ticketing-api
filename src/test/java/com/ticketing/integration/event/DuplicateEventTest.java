package com.ticketing.integration.event;

import com.ticketing.dto.event.EventRequest;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.EventRepository;
import com.ticketing.security.AuthUser;
import com.ticketing.support.Concurrently;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DuplicateEventTest extends IntegrationTest {

    private static final Instant START = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private EventRepository eventRepository;

    @Test
    void sameEventCannotBeCreatedTwice() throws Exception {
        String body = """
                {"title":"%s","venue":"Babylon","startsAt":"%s","endsAt":"%s","capacity":100}
                """.formatted(uniqueTitle(), START, START.plus(3, ChronoUnit.HOURS));

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_EMAIL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An event with the same title, venue and start time already exists"));
    }

    @Test
    void sameConcertAtAnotherTimeIsAllowed() {
        String title = uniqueTitle();
        eventService.create(request(title, START), organizer());

        assertThat(eventService.create(request(title, START.plus(3, ChronoUnit.HOURS)), organizer()).id()).isNotNull();
    }

    @Test
    void updateCanKeepItsOwnTitleVenueAndStart() {
        String title = uniqueTitle();
        Long eventId = eventService.create(request(title, START), organizer()).id();

        EventRequest sameSlotMoreSeats = new EventRequest(title, "Babylon", START, START.plus(3, ChronoUnit.HOURS), 200);
        assertThat(eventService.update(eventId, sameSlotMoreSeats, organizer()).capacity()).isEqualTo(200);
    }

    @Test
    void updateCannotMoveIntoAnotherEventsSlot() {
        String title = uniqueTitle();
        eventService.create(request(title, START), organizer());
        Long laterShow = eventService.create(request(title, START.plus(3, ChronoUnit.HOURS)), organizer()).id();

        assertThatThrownBy(() -> eventService.update(laterShow, request(title, START), organizer()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void parallelRequestsForTheSameEventCreateOnlyOne() throws Exception {
        String title = uniqueTitle();
        AuthUser organizer = organizer();

        List<Throwable> errors = Concurrently.run(10, i -> eventService.create(request(title, START), organizer));

        // most requests stop at the exists check, the ones that slip past it hit the unique constraint
        assertThat(errors).hasSize(9).allSatisfy(error -> assertThat(error)
                .isInstanceOfAny(ApiException.class, DataIntegrityViolationException.class));
        assertThat(eventRepository.findByOwnerId(organizer.id()))
                .filteredOn(event -> event.getTitle().equals(title))
                .hasSize(1);
    }

    private EventRequest request(String title, Instant startsAt) {
        return new EventRequest(title, "Babylon", startsAt, startsAt.plus(2, ChronoUnit.HOURS), 100);
    }

    private String uniqueTitle() {
        return "Concert " + UUID.randomUUID();
    }
}
