package com.ticketing.service;

import com.ticketing.entity.IdempotencyKey;
import com.ticketing.entity.IdempotencyStatus;
import com.ticketing.exception.ApiException;
import com.ticketing.repository.IdempotencyKeyRepository;
import com.ticketing.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final long IDEMPOTENCY_TTL_SECONDS = 86400L;

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * Rejects blank keys and keys longer than the column allows (400).
     */
    public void validateKey(String key) {
        if (key.isBlank() || key.length() > IdempotencyKey.MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be between 1 and " + IdempotencyKey.MAX_KEY_LENGTH + " characters");
        }
    }

    /**
     * Returns the id of the resource created by an earlier request with the same key, if any.
     * An expired key is deleted and treated as unused. A key reused with a different
     * request body is rejected with 400.
     */
    public Optional<Long> resolveExistingResourceId(String key, String endpoint, Long userId, String requestFingerprint) {
        return findActive(key, endpoint, userId).map(existing -> {
            if (!existing.getRequestHash().equals(HashUtils.sha256(requestFingerprint))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key was already used with a different request");
            }
            return existing.getResourceId();
        });
    }

    /**
     * Stores the key with the request hash. It runs in the caller's transaction, so if the
     * reservation fails the key is rolled back too and the client can retry with it.
     */
    public IdempotencyKey start(String key, String endpoint, Long userId, String requestFingerprint) {
        IdempotencyKey keyRecord = new IdempotencyKey();
        keyRecord.setKey(key);
        keyRecord.setEndpoint(endpoint);
        keyRecord.setUserId(userId);
        keyRecord.setRequestHash(HashUtils.sha256(requestFingerprint));
        keyRecord.setTtl(IDEMPOTENCY_TTL_SECONDS);
        return idempotencyKeyRepository.saveAndFlush(keyRecord);
    }

    /**
     * Links the key to the created resource and marks it completed.
     */
    public void complete(IdempotencyKey idempotencyKey, Long resourceId, String responseBody) {
        idempotencyKey.setResourceId(resourceId);
        idempotencyKey.setResponseHash(HashUtils.sha256(responseBody));
        idempotencyKey.setStatus(IdempotencyStatus.COMPLETED);
        idempotencyKeyRepository.saveAndFlush(idempotencyKey);
    }

    /**
     * Returns the stored key unless it has expired. An expired key is deleted and flushed
     * right away: Hibernate runs inserts before deletes on flush, so without it the new key
     * for the same request would hit the unique constraint.
     */
    private Optional<IdempotencyKey> findActive(String key, String endpoint, Long userId) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByKeyAndEndpointAndUserId(key, endpoint, userId);
        if (existing.isPresent() && isExpired(existing.get())) {
            idempotencyKeyRepository.delete(existing.get());
            idempotencyKeyRepository.flush();
            return Optional.empty();
        }
        return existing;
    }

    /**
     * A key expires ttl seconds after it was created.
     */
    private boolean isExpired(IdempotencyKey keyRecord) {
        return keyRecord.getCreatedAt().plusSeconds(keyRecord.getTtl()).isBefore(Instant.now());
    }
}
