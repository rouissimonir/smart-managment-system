package com.smartmgmt.management.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmgmt.management.user.dto.UserSummary;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Enabled accounts only, so a disabled user cannot be newly assigned. */
    public List<UserSummary> listEnabled() {
        return users.findByEnabledTrueOrderByFullNameAscEmailAsc().stream()
                .map(UserSummary::from)
                .toList();
    }
}
