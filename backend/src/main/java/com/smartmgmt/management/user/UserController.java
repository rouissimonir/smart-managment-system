package com.smartmgmt.management.user;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartmgmt.management.user.dto.UserSummary;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * Assignment directory for project owners and task assignees.
     * Restricted to people who can create projects and tasks.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping
    public List<UserSummary> list() {
        return service.listEnabled();
    }
}
