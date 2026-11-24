package com.smartmgmt.management.user.dto;

import java.util.UUID;

import com.smartmgmt.management.user.Role;
import com.smartmgmt.management.user.User;

/**
 * Directory entry for assignment pickers. Deliberately has no password hash
 * and no other credential material.
 */
public record UserSummary(UUID id, String email, String fullName, Role role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
