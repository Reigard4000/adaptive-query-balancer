package com.mullagaliev.aqpbalancer.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Protects administrative endpoints.
 *
 * <p>If {@code balancer.admin.token} is set, every mutating administrative call
 * must carry it in {@code X-Admin-Token} or as a Bearer token. Without a
 * configured token the endpoints are available only from loopback (or fully
 * denied when {@code balancer.admin.allow-loopback=false}).</p>
 */
@Component
public class AdminAccessFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/servers",
            "/api/monitor/reset",
            "/api/simulate",
            "/api/stream"
    );

    private final String adminToken;
    private final boolean allowLoopback;

    public AdminAccessFilter(
            @Value("${balancer.admin.token:}") String adminToken,
            @Value("${balancer.admin.allow-loopback:true}") boolean allowLoopback
    ) {
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.allowLoopback = allowLoopback;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Admin access denied\"}");
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        if (!adminToken.isBlank()) {
            String provided = request.getHeader("X-Admin-Token");
            if (provided == null || provided.isBlank()) {
                provided = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
            }
            return provided != null
                    && MessageDigest.isEqual(
                    adminToken.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8)
            );
        }
        return allowLoopback && isLoopback(request.getRemoteAddr());
    }

    private String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorization.substring(prefix.length()).trim();
        }
        return null;
    }

    private boolean isLoopback(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }
}
