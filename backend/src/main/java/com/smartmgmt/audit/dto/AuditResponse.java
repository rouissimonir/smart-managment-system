package com.smartmgmt.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.smartmgmt.audit.AuditEvent;

public record AuditResponse(
        UUID id,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        String detail,
        Instant createdAt) {

    public static AuditResponse from(AuditEvent event) {
        return new AuditResponse(
                event.getId(),
                event.getActor() == null ? null : event.getActor().getEmail(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetail(),
                event.getCreatedAt());
    }
}
