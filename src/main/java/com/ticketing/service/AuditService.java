package com.ticketing.service;

import com.ticketing.entity.AuditLog;
import com.ticketing.entity.AuditAction;
import com.ticketing.entity.ResourceType;
import com.ticketing.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private final AuditLogRepository auditLogRepository;

    /**
     * Writes an entry in the caller's transaction, so it is only kept if the operation commits.
     */
    @Transactional
    public void record(Long actorId, AuditAction action, ResourceType resourceType, Long resourceId) {
        auditLogRepository.save(build(actorId, action, resourceType, resourceId));
    }

    /**
     * Use this for failed operations. It writes in a new transaction, so the entry is kept
     * even when the caller's transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long actorId, AuditAction action, ResourceType resourceType, Long resourceId) {
        auditLogRepository.save(build(actorId, action, resourceType, resourceId));
    }

    /**
     * Creates the entry and fills in IP and user agent from the current request, if there is one.
     */
    private AuditLog build(Long actorId, AuditAction action, ResourceType resourceType, Long resourceId) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorId(actorId);
        auditLog.setAction(action);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId);

        HttpServletRequest request = currentRequest();
        if (request != null) {
            auditLog.setIp(request.getRemoteAddr());
            auditLog.setUserAgent(truncate(request.getHeader(HttpHeaders.USER_AGENT)));
        }
        return auditLog;
    }

    /**
     * The request bound to this thread, or null outside a web request (tests, background jobs).
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * Keeps the user agent within the column size.
     */
    private String truncate(String value) {
        return value != null && value.length() > USER_AGENT_MAX_LENGTH
                ? value.substring(0, USER_AGENT_MAX_LENGTH)
                : value;
    }
}
