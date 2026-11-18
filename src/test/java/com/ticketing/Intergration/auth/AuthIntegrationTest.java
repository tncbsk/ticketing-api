package com.ticketing.Intergration.auth;

import com.ticketing.entity.User;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Passw0rd!x";

    @Test
    void registerCreatesCustomerByDefault() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginReturnsTokensAndUpdatesLastLogin() throws Exception {
        Instant before = Instant.now();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CUSTOMER_EMAIL + "\",\"password\":\"Customer123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        User user = userRepository.findByEmail(CUSTOMER_EMAIL).orElseThrow();
        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + CUSTOMER_EMAIL + "\",\"password\":\"wrong-password!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }
}
