package com.ticketing.service;

import com.ticketing.entity.IdempotencyKey;
import com.ticketing.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final long IDEMPOTENCY_TTL_SECONDS = 86400L;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public Optional<IdempotencyKey> findActive(String idempotencyKey, String endPoint, Long userId) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByKeyAndEndpointAndUserId(idempotencyKey, endPoint, userId);
        if (existing.isPresent() && isExpired(existing.get())) {
            idempotencyKeyRepository.delete(existing.get());
            idempotencyKeyRepository.flush();
            return Optional.empty();
        }
        return existing;
    }

    public void saveIdempotency(IdempotencyKey idempotencyKey) {
        idempotencyKeyRepository.saveAndFlush(idempotencyKey);
    }

    private boolean isExpired(IdempotencyKey record) {
        return record.getCreatedAt().plusSeconds(record.getTtl()).isBefore(LocalDateTime.now());
    }
}
