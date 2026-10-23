package com.capstone.agenticrag.security;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.capstone.agenticrag.config.AppProperties;

/** Throttles expensive operations: AI chat, document uploads and authentication attempts. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final AppProperties.RateLimit limits;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, AppProperties props, ObjectMapper mapper) {
        this.limiter = limiter;
        this.limits = props.rateLimit();
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = bucketFor(request);
        if (bucket != null) {
            int limit = switch (bucket) {
                case "chat" -> limits.chatPerMinute();
                case "upload" -> limits.uploadPerMinute();
                default -> limits.authPerMinute();
            };
            if (!limiter.tryAcquire(bucket + ":" + clientKey(request), limit)) {
                response.setHeader("Retry-After", "60");
                SecurityConfig.write(mapper, request, response, 429, "RATE_LIMITED",
                        "Too many requests. Please wait a moment and try again.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String bucketFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/chat/") && !path.endsWith("/cancel")) {
            return "chat";
        }
        if (path.equals("/api/documents") || path.matches("/api/documents/[^/]+/reingest")) {
            return "upload";
        }
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.startsWith("/api/auth/password-reset")) {
            return "auth";
        }
        return null;
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p.id().toString();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** Registered only inside the Spring Security chain, not as a standalone servlet filter. */
    @Configuration
    static class Registration {
        @Bean
        FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
            FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
            reg.setEnabled(false);
            return reg;
        }
    }
}
