package com.example.banking.monitor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns every API request a correlation id (X-Request-ID), exposes it in the
 * response header, and publishes REQUEST / RESPONSE flow events with the real
 * HTTP status and duration. Runs before the security chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    private final FlowEventBus bus;

    public RequestIdFilter(FlowEventBus bus) {
        this.bus = bus;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = nextId();
        }
        String path = request.getRequestURI();
        FlowContext.init(requestId, request.getMethod(), path);
        response.setHeader("X-Request-ID", requestId);

        boolean isMonitorStream = path.startsWith("/api/monitor/");
        boolean isStatic = path.equals("/flow.html") || path.equals("/")
                || path.startsWith("/flow") || path.endsWith(".css") || path.endsWith(".js")
                || path.startsWith("/actuator");
        long start = System.nanoTime();
        boolean success = true;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean authed = auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
            if (!isMonitorStream && !isStatic) {
                // Published via bus directly to avoid depending on FlowPublisher's ctx user.
                FlowEvent e = FlowEvent.builder()
                        .requestId(requestId)
                        .type("REQUEST").layer("CLIENT").status("RUNNING")
                        .operation(FlowContext.deriveOperation(request.getMethod(), path))
                        .method(request.getMethod()).endpoint(path)
                        .message("API request received: " + request.getMethod() + " " + path)
                        .authenticated(authed)
                        .build();
                bus.publish(e);
            }
            chain.doFilter(request, response);
            success = response.getStatus() < 400;
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (!isMonitorStream && !isStatic) {
                int status = response.getStatus();
                // If an exception propagated, status may still be 200; mark failed.
                FlowEvent e = FlowEvent.builder()
                        .requestId(requestId)
                        .type("RESPONSE").layer("CONTROLLER")
                        .status(success && status < 400 ? "SUCCESS" : "FAILED")
                        .operation(FlowContext.deriveOperation(request.getMethod(), path))
                        .method(request.getMethod()).endpoint(path)
                        .httpStatus(status)
                        .durationMs(durationMs)
                        .message("Response " + status + " in " + durationMs + " ms")
                        .build();
                bus.publish(e);
            }
            FlowContext.clear();
        }
    }

    private static String nextId() {
        long n = COUNTER.incrementAndGet();
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("REQ-%s-%05d", date, n % 100000);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
