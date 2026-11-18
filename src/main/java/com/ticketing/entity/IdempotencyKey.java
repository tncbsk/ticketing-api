package com.ticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key_endpoint_user_id", columnNames = {"idem_key", "endpoint", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" is a reserved word in H2
    @Column(name = "idem_key", nullable = false, length = 100)
    private String key;

    @Column(name = "user_id" , nullable = false)
    private Long userId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "response_hash", length = 64)
    private String responseHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IdempotencyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Long ttl;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
