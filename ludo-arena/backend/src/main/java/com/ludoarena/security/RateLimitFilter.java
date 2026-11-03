package com.ludoarena.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.config.AppProperties;
import com.ludoarena.dto.Dtos.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, per-client-IP limiter for the sensitive authentication endpoints.
 * In-memory: correct for a single instance; use a shared store (Redis) when scaling horizontally.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000L;

    private static final class Window {
        long start;
        int count;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Map<String, Integer> limits;
    private final boolean enabled;
    private final boolean trustProxy;
    private final ObjectMapper mapper;
    private long lastSweep = System.currentTimeMillis();

    public RateLimitFilter(AppProperties props, ObjectMapper mapper) {
        AppProperties.RateLimit rl = props.rateLimit();
        this.enabled = rl.enabled();
        this.trustProxy = props.trustProxy();
        this.mapper = mapper;
        this.limits = Map.of(
                "/api/v1/auth/login", rl.loginPerMinute(),
                "/api/v1/auth/register", rl.registerPerMinute(),
                "/api/v1/auth/forgot-password", rl.forgotPasswordPerMinute(),
                "/api/v1/auth/reset-password", rl.resetPasswordPerMinute(),
                "/api/v1/auth/refresh", rl.refreshPerMinute());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equalsIgnoreCase(request.getMethod()) || !limits.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        String key = clientIp(req) + "|" + path;
        long now = System.currentTimeMillis();
        sweep(now);
        Window w = windows.computeIfAbsent(key, k -> new Window());
        boolean allowed;
        long retryAfterSec;
        synchronized (w) {
            if (now - w.start >= WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            w.count++;
            allowed = w.count <= limits.get(path);
            retryAfterSec = Math.max(1, (WINDOW_MS - (now - w.start)) / 1000);
        }
        if (!allowed) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retryAfterSec));
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(res.getOutputStream(),
                    ApiResponse.error("RATE_LIMITED", "Too many attempts. Try again in " + retryAfterSec + "s.", null));
            return;
        }
        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        if (trustProxy) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private void sweep(long now) {
        if (now - lastSweep < WINDOW_MS) return;
        lastSweep = now;
        windows.entrySet().removeIf(e -> now - e.getValue().start >= 2 * WINDOW_MS);
    }
}
