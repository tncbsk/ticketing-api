package com.ticketing.Intergration.audit;

import com.ticketing.entity.AuditAction;
import com.ticketing.entity.AuditLog;
import com.ticketing.entity.ResourceType;
import com.ticketing.entity.Role;
import com.ticketing.entity.User;
import com.ticketing.repository.AuditLogRepository;
import com.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogIntegrationTest extends IntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void failedLoginIsRecordedEvenThoughLoginRollsBack() throws Exception {
        User target = createUser(Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "audit-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + target.getEmail() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = logsFor(AuditAction.LOGIN_FAILED, target.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActorId()).isNull();
        assertThat(logs.getFirst().getResourceType()).isEqualTo(ResourceType.USER);
        assertThat(logs.getFirst().getIp()).isNotBlank();
        assertThat(logs.getFirst().getUserAgent()).isEqualTo("audit-test");
    }

    @Test
    void successfulLoginIsRecordedWithActor() throws Exception {
        User target = createUser(Role.CUSTOMER);
        target.setPasswordHash(passwordEncoder.encode("Pass"));
        userRepository.save(target);

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "audit-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + target.getEmail() + "\",\"password\":\"Pass\"}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = logsFor(AuditAction.LOGIN_SUCCESS, target.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getActorId()).isEqualTo(target.getId());
        assertThat(logs.getFirst().getIp()).isNotBlank();
        assertThat(logs.getFirst().getUserAgent()).isEqualTo("audit-test");
    }

    private List<AuditLog> logsFor(AuditAction action, Long resourceId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action && resourceId.equals(log.getResourceId()))
                .toList();
    }
}
