package com.ticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key_endpoint", columnNames = {"idem_key", "endpoint"}))
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

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "response_hash", length = 64)
    private String responseHash;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // seconds
    private Long ttl;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
