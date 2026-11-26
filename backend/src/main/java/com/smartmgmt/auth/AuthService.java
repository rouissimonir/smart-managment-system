package com.smartmgmt.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartmgmt.audit.AuditService;
import com.smartmgmt.auth.dto.LoginRequest;
import com.smartmgmt.auth.dto.LoginResponse;
import com.smartmgmt.auth.dto.MeResponse;
import com.smartmgmt.auth.dto.RefreshRequest;
import com.smartmgmt.management.user.User;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(AuthenticationManager authenticationManager, RefreshTokenRepository refreshTokens,
            JwtService jwtService, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException | DisabledException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        audit.record(user, "LOGIN", "USER", user.getId(), user.getEmail());
        return issueTokens(user, null);
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken stored = loadRefreshToken(request.refreshToken());
        if (stored.isRevoked()) {
            if (stored.getFamilyId() != null) {
                refreshTokens.revokeFamily(stored.getFamilyId());
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }
        if (!stored.getUser().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled");
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);
        return issueTokens(stored.getUser(), stored.getFamilyId());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        refreshTokens.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokens.save(rt);
        });
    }

    private RefreshToken loadRefreshToken(String rawToken) {
        String hash = jwtService.hashRefreshToken(rawToken);
        return refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    }

    private LoginResponse issueTokens(User user, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setFamilyId(familyId == null ? UUID.randomUUID() : familyId);
        refreshToken.setTokenHash(jwtService.hashRefreshToken(refreshTokenValue));
        refreshToken.setExpiresAt(Instant.now().plus(jwtService.refreshTokenTtl()));
        refreshTokens.save(refreshToken);

        return new LoginResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                jwtService.accessTokenTtl().toSeconds(),
                MeResponse.from(user));
    }
}
