package com.smartmgmt.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps POST /api/auth/login. Registered only on the security chain.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter limiter;

    public LoginRateLimitFilter(LoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath())) {
            String key = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
            if (!limiter.allow(key)) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.getWriter().write(
                        "{\"title\":\"Too many login attempts\",\"status\":429,\"detail\":\"Try again in a minute.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
