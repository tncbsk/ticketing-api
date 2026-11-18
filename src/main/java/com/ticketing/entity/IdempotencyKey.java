package com.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    public static final int MAX_KEY_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "key" is a reserved word in H2
    @Column(name = "idem_key", nullable = false, length = MAX_KEY_LENGTH)
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
