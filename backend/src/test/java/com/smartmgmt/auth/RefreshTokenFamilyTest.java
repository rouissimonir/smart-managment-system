package com.smartmgmt.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.server.ResponseStatusException;

import com.smartmgmt.audit.AuditService;
import com.smartmgmt.auth.dto.RefreshRequest;
import com.smartmgmt.management.user.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFamilyTest {

    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    RefreshTokenRepository refreshTokens;
    @Mock
    JwtService jwtService;
    @Mock
    AuditService audit;

    @Test
    void replayingARevokedTokenRevokesTheFamily() {
        UUID familyId = UUID.randomUUID();
        User user = new User();
        user.setEnabled(true);
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setRevoked(true);
        stored.setFamilyId(familyId);
        stored.setExpiresAt(Instant.now().plusSeconds(60));

        when(jwtService.hashRefreshToken("stolen")).thenReturn("hash");
        when(refreshTokens.findByTokenHash("hash")).thenReturn(Optional.of(stored));

        AuthService service = new AuthService(authenticationManager, refreshTokens, jwtService, audit);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.refresh(new RefreshRequest("stolen")));

        assertEquals(401, error.getStatusCode().value());
        verify(refreshTokens).revokeFamily(familyId);
    }
}
