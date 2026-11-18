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

    @Transactional
    public void record(Long actorId, AuditAction action, ResourceType resourceType, Long resourceId) {
        auditLogRepository.save(build(actorId, action, resourceType, resourceId));
    }

    // failed operations roll back their own transaction, so the audit entry needs its own
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long actorId, AuditAction action, ResourceType resourceType, Long resourceId) {
        auditLogRepository.save(build(actorId, action, resourceType, resourceId));
    }

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

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value) {
        return value != null && value.length() > USER_AGENT_MAX_LENGTH
                ? value.substring(0, USER_AGENT_MAX_LENGTH)
                : value;
    }
}
