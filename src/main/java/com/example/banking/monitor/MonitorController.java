package com.example.banking.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only monitoring endpoints (public for classroom demo; they expose only
 * sanitized flow events and aggregate status, never secrets or hashes).
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final FlowEventBus bus;
    private final JdbcTemplate jdbc;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    public MonitorController(FlowEventBus bus, JdbcTemplate jdbc) {
        this.bus = bus;
        this.jdbc = jdbc;
    }

    /** Backend -> browser live stream (EventSource in flow.js subscribes here). */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return bus.subscribe();
    }

    /** Bounded recent-event history for page load / reconnect. */
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "100") int limit) {
        List<FlowEvent> events = bus.recent(limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("count", events.size());
        out.put("data", events);
        return out;
    }

    @GetMapping("/clear")
    public Map<String, Object> clear() {
        bus.clear();
        return Map.of("success", true, "message", "Event history cleared");
    }

    /** Real runtime status: app up, DB connectivity probed with SELECT 1. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);

        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("running", true);
        backend.put("application", "Banking System Backend");
        backend.put("port", serverPort);
        backend.put("subscribers", bus.subscriberCount());
        out.put("backend", backend);

        Map<String, Object> db = new LinkedHashMap<>();
        boolean connected = false;
        String engine = "PostgreSQL";
        String database = parseDbName(dbUrl);
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            connected = one != null && one == 1;
        } catch (Exception ex) {
            connected = false;
            db.put("error", "Connection failed");
        }
        db.put("connected", connected);
        db.put("status", connected ? "CONNECTED" : "DISCONNECTED");
        db.put("database", database);
        db.put("engine", engine);
        out.put("database", db);

        Map<String, Object> api = new LinkedHashMap<>();
        api.put("available", true);
        api.put("stream", "/api/monitor/events");
        out.put("api", api);
        return out;
    }

    private static String parseDbName(String url) {
        if (url == null || url.isBlank()) return "banking_system_db";
        try {
            String noParams = url.split("\\?")[0];
            int slash = noParams.lastIndexOf('/');
            if (slash >= 0 && slash < noParams.length() - 1) {
                return noParams.substring(slash + 1);
            }
        } catch (Exception ignored) { /* fall through */ }
        return "banking_system_db";
    }
}
