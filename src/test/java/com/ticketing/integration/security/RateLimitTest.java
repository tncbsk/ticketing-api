package com.ticketing.integration.security;

import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitTest extends IntegrationTest {

    private static final int LIMIT = 10;

    // 25 > 2 * limit, so we still get a 429 if the minute changes during the test
    private static final int ATTEMPTS = 2 * LIMIT + 5;

    @Test
    void loginIsLimitedPerIp() throws Exception {
        List<Integer> statuses = loginAttempts("10.0.0.1");

        assertThat(statuses.subList(0, LIMIT)).containsOnly(401);
        assertThat(statuses).contains(429);
    }

    private List<Integer> loginAttempts(String ip) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            statuses.add(mockMvc.perform(login(ip)).andReturn().getResponse().getStatus());
        }
        return statuses;
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return post("/api/auth/login")
                .with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + CUSTOMER_EMAIL + "\",\"password\":\"wrong-password\"}");
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
