package com.ticketing.service;

import com.ticketing.entity.IdempotencyKey;
import com.ticketing.entity.IdempotencyStatus;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.IdempotencyKeyRepository;
import com.ticketing.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final long IDEMPOTENCY_TTL_SECONDS = 86400L;

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public Optional<Long> findCompletedResourceId(String key, String endpoint, Long userId, String requestBody) {
        return findActive(key, endpoint, userId).map(existing -> {
            if (!existing.getRequestHash().equals(HashUtils.sha256(requestBody))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key was already used with a different request");
            }
            return existing.getResourceId();
        });
    }

    public IdempotencyKey start(String key, String endpoint, Long userId, String requestBody) {
        IdempotencyKey keyRecord = new IdempotencyKey();
        keyRecord.setKey(key);
        keyRecord.setEndpoint(endpoint);
        keyRecord.setUserId(userId);
        keyRecord.setRequestHash(HashUtils.sha256(requestBody));
        keyRecord.setTtl(IDEMPOTENCY_TTL_SECONDS);
        return idempotencyKeyRepository.saveAndFlush(keyRecord);
    }

    public void complete(IdempotencyKey keyRecord, Long resourceId, String responseBody) {
        keyRecord.setResourceId(resourceId);
        keyRecord.setResponseHash(HashUtils.sha256(responseBody));
        keyRecord.setStatus(IdempotencyStatus.COMPLETED);
        idempotencyKeyRepository.saveAndFlush(keyRecord);
    }

    private Optional<IdempotencyKey> findActive(String key, String endpoint, Long userId) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByKeyAndEndpointAndUserId(key, endpoint, userId);
        if (existing.isPresent() && isExpired(existing.get())) {
            idempotencyKeyRepository.delete(existing.get());
            idempotencyKeyRepository.flush();
            return Optional.empty();
        }
        return existing;
    }

    private boolean isExpired(IdempotencyKey keyRecord) {
        return keyRecord.getCreatedAt().plusSeconds(keyRecord.getTtl()).isBefore(LocalDateTime.now());
    }
}
