package com.example.price.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every business HTTP request with method, target, status and duration at
 * INFO. Infrastructure endpoints (health probes, swagger) are skipped so
 * periodic checks do not flood the log.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final List<String> INFRA_PATH_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isInfraPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            String target = request.getQueryString() == null
                    ? request.getRequestURI()
                    : request.getRequestURI() + "?" + request.getQueryString();
            log.info("{} {} -> {} ({} ms)", request.getMethod(), target, response.getStatus(), elapsedMs);
        }
    }

    private static boolean isInfraPath(String uri) {
        return INFRA_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
