package com.smartmgmt.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, UUID> {

    @EntityGraph(attributePaths = "actor")
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
