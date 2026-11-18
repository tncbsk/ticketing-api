package com.ticketing.support;

import com.ticketing.dto.event.EventRequest;
import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.repository.UserRepository;
import com.ticketing.security.AuthUser;
import com.ticketing.security.JwtService;
import com.ticketing.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String ORGANIZER_EMAIL = "organizer@ticketing.local";
    protected static final String CUSTOMER_EMAIL = "customer@ticketing.local";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected EventService eventService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    protected AuthUser organizer() {
        return authUser(ORGANIZER_EMAIL);
    }

    protected AuthUser customer() {
        return authUser(CUSTOMER_EMAIL);
    }

    private AuthUser authUser(String email) {
        User user = findUser(email);
        return new AuthUser(user.getId(), user.getEmail(), Set.copyOf(user.getRoles()));
    }

    // "Bearer <token>" for the Authorization header, without going through login
    protected String bearer(String email) {
        return "Bearer " + jwtService.generateAccessToken(findUser(email));
    }

    protected User createUser(Role role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        user.setPasswordHash("not-used");
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    protected EventRequest eventRequest(int capacity) {
        Instant startsAt = Instant.now().plus(10, ChronoUnit.DAYS);
        return new EventRequest("Test event", "Test venue", startsAt, startsAt.plus(2, ChronoUnit.HOURS), capacity);
    }

    protected String eventJson(int capacity) {
        EventRequest request = eventRequest(capacity);
        return """
                {"title":"%s","venue":"%s","startsAt":"%s","endsAt":"%s","capacity":%d}
                """.formatted(request.title(), request.venue(), request.startsAt(), request.endsAt(), capacity);
    }

    protected Long createPublishedEvent(int capacity) {
        Long eventId = eventService.create(eventRequest(capacity), organizer()).id();
        eventService.publish(eventId, organizer());
        return eventId;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }
}
