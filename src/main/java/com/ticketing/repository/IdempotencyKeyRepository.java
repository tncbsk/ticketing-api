package com.ticketing.repository;

import com.ticketing.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByKeyAndEndpointAndUserId(String key, String endpoint, Long userId);
}
