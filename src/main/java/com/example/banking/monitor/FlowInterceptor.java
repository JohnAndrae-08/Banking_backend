package com.example.banking.monitor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Publishes CONTROLLER-layer events: runs inside the DispatcherServlet after
 * security, so it marks the true controller entry/exit distinct from the
 * CLIENT request event published by {@link RequestIdFilter}.
 */
@Component
public class FlowInterceptor implements HandlerInterceptor {

    private final FlowPublisher flow;

    public FlowInterceptor(FlowPublisher flow) {
        this.flow = flow;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/monitor")) {
            return true;
        }
        String name = null;
        if (handler instanceof HandlerMethod hm) {
            name = hm.getBeanType().getSimpleName() + "." + hm.getMethod().getName();
        }
        flow.controllerReceived(name);
        return true;
    }
}
