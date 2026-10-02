package com.example.banking.monitor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the live-flow monitoring pipeline.
 * No database required: verifies event bus behavior, sanitization,
 * operation mapping, and SQL normalization.
 */
class FlowMonitorTest {

    @Test
    void busKeepsBoundedHistoryAndReplays() {
        FlowEventBus bus = new FlowEventBus();
        for (int i = 0; i < 10; i++) {
            FlowEvent e = FlowEvent.builder().requestId("REQ-" + i)
                    .type("SERVICE").layer("SERVICE").status("RUNNING")
                    .message("step " + i).build();
            bus.publish(e);
        }
        List<FlowEvent> last3 = bus.recent(3);
        assertEquals(3, last3.size());
        assertEquals("REQ-9", last3.get(2).getRequestId());

        // Subscriber receives replay without error.
        assertDoesNotThrow(bus::subscribe);
        assertEquals(1, bus.subscriberCount());
    }

    @Test
    void historyLimitIsClamped() {
        FlowEventBus bus = new FlowEventBus();
        bus.publish(FlowEvent.builder().requestId("A").type("SQL").build());
        assertEquals(1, bus.recent(0).size());
        assertEquals(1, bus.recent(500).size());
    }

    @Test
    void publisherMasksSensitiveData() {
        assertEquals("Authorization: Bearer ********",
                FlowPublisher.sanitize("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
        assertEquals("password: ********",
                FlowPublisher.sanitize("password: supersecret"));
        assertEquals("jwt_secret: ********",
                FlowPublisher.sanitize("jwt_secret: abc123"));
        assertNull(FlowPublisher.sanitize(null));
    }

    @Test
    void sqlIsNormalizedToSingleLine() {
        String raw = "SELECT *\n   FROM accounts\n WHERE id = ?";
        String normalized = FlowPublisher.normalizeSql(raw);
        assertEquals("SELECT * FROM accounts WHERE id = ?", normalized);
    }

    @Test
    void operationMappingCoversBankingEndpoints() {
        assertEquals("LOGIN", FlowContext.deriveOperation("POST", "/api/auth/login"));
        assertEquals("REGISTER", FlowContext.deriveOperation("POST", "/api/auth/register"));
        assertEquals("DEPOSIT", FlowContext.deriveOperation("POST", "/api/accounts/1/deposit"));
        assertEquals("WITHDRAW", FlowContext.deriveOperation("POST", "/api/accounts/1/withdraw"));
        assertEquals("BALANCE", FlowContext.deriveOperation("GET", "/api/accounts/1/balance"));
        assertEquals("TRANSFER", FlowContext.deriveOperation("POST", "/api/transfers"));
        assertEquals("HISTORY", FlowContext.deriveOperation("GET", "/api/accounts/1/transactions"));
        assertEquals("CREATE_ACCOUNT", FlowContext.deriveOperation("POST", "/api/accounts"));
        assertEquals("MONITOR", FlowContext.deriveOperation("GET", "/api/monitor/status"));
    }

    @Test
    void flowEventCarriesCorrelationFields() {
        FlowEvent e = FlowEvent.builder()
                .requestId("REQ-20240101-00001")
                .type("SQL").layer("SQL").operation("TRANSFER")
                .method("POST").endpoint("/api/transfers")
                .status("RUNNING")
                .sql("UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?")
                .tables(List.of("accounts"))
                .build();
        assertEquals("REQ-20240101-00001", e.getRequestId());
        assertEquals("TRANSFER", e.getOperation());
        assertTrue(e.getSql().contains("UPDATE accounts"));
        assertNotNull(e.getTimestamp());
    }
}
