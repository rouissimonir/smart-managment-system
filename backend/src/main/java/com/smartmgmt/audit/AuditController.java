package com.smartmgmt.audit;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartmgmt.audit.dto.AuditResponse;
import com.smartmgmt.common.PageResponse;
import com.smartmgmt.common.SortSupport;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "action", "entityType");

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<AuditResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SortSupport.validate(pageable.getSort(), SORTABLE_FIELDS);
        return PageResponse.from(audit.list(pageable).map(AuditResponse::from));
    }
}
