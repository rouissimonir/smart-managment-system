package com.smartmgmt.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmgmt.management.user.User;

@Service
public class AuditService {

    private final AuditRepository events;

    public AuditService(AuditRepository events) {
        this.events = events;
    }

    @Transactional
    public void record(User actor, String action, String entityType, UUID entityId, String detail) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetail(trim(detail));
        events.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(Pageable pageable) {
        return events.findAllByOrderByCreatedAtDesc(pageable);
    }

    private static String trim(String detail) {
        if (detail == null || detail.length() <= 500) {
            return detail;
        }
        return detail.substring(0, 500);
    }
}
