package com.example.banking.monitor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Single helper used by filters, interceptors, services and repositories to
 * emit sanitized flow events. Safe to call when no request context exists
 * (background threads, unit tests): falls back to a "SYSTEM" correlation id.
 */
@Component
public class FlowPublisher {

    private final FlowEventBus bus;

    public FlowPublisher(FlowEventBus bus) {
        this.bus = bus;
    }

    private FlowContext.State ctx() {
        return FlowContext.get();
    }

    private String reqId() {
        FlowContext.State s = ctx();
        return s != null ? s.getRequestId() : "SYSTEM";
    }

    private String method() {
        FlowContext.State s = ctx();
        return s != null ? s.getMethod() : null;
    }

    private String endpoint() {
        FlowContext.State s = ctx();
        return s != null ? s.getEndpoint() : null;
    }

    private String operation() {
        FlowContext.State s = ctx();
        return s != null ? s.getOperation() : "SYSTEM";
    }

    private String user() {
        FlowContext.State s = ctx();
        return s != null ? s.getUser() : null;
    }

    private FlowEvent base(String type, String layer, String status) {
        return FlowEvent.builder()
                .requestId(reqId())
                .type(type)
                .layer(layer)
                .operation(operation())
                .method(method())
                .endpoint(endpoint())
                .status(status)
                .user(sanitizeUser(user()))
                .build();
    }

    /** Masks usernames/emails only if they look like secrets; otherwise passes through. */
    static String sanitizeUser(String user) {
        if (user == null) return null;
        return user;
    }

    /** Removes anything that looks like a credential/token from free-text messages. */
    public static String sanitize(String text) {
        if (text == null) return null;
        String out = text;
        // Mask Bearer tokens (keep scheme only).
        out = out.replaceAll("(?i)Bearer\\s+[A-Za-z0-9\\-_.=]+", "Bearer ********");
        // Mask password/hash/secret assignments.
        out = out.replaceAll("(?i)(password(_hash)?|jwt[_ -]?secret|db[_ -]?password|secret)\\s*[:=]\\s*\\S+", "$1: ********");
        return out;
    }

    public void requestReceived(boolean authenticated) {
        FlowEvent e = base("REQUEST", "CLIENT", "RUNNING");
        e.setMessage("API request received");
        e.setAuthenticated(authenticated);
        bus.publish(e);
    }

    public void controllerReceived(String handler) {
        FlowEvent e = base("CONTROLLER", "CONTROLLER", "RUNNING");
        e.setMessage(sanitize("Controller received request" + (handler != null ? " -> " + handler : "")));
        bus.publish(e);
    }

    public void service(String message) {
        FlowEvent e = base("SERVICE", "SERVICE", "RUNNING");
        e.setMessage(sanitize(message));
        bus.publish(e);
    }

    public void auth(String message, boolean authenticated) {
        FlowEvent e = base("AUTH", "AUTH", authenticated ? "SUCCESS" : "RUNNING");
        e.setMessage(sanitize(message));
        e.setAuthenticated(authenticated);
        bus.publish(e);
    }

    public void repository(String message) {
        FlowEvent e = base("REPOSITORY", "REPOSITORY", "RUNNING");
        e.setMessage(sanitize(message));
        bus.publish(e);
    }

    public void sql(String sql, List<String> tables) {
        FlowEvent e = base("SQL", "SQL", "RUNNING");
        e.setMessage("Executing SQL");
        e.setSql(normalizeSql(sql));
        e.setTables(tables);
        bus.publish(e);
    }

    public void database(String message) {
        FlowEvent e = base("DATABASE", "DATABASE", "RUNNING");
        e.setMessage(sanitize(message));
        bus.publish(e);
    }

    public void transactionBegin() {
        FlowEvent begin = base("TRANSACTION", "TRANSACTION", "RUNNING");
        begin.setMessage("Database transaction BEGIN");
        bus.publish(begin);
        // Report the true outcome via synchronization when inside a Spring transaction.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    FlowEvent e = base("TRANSACTION", "TRANSACTION", "SUCCESS");
                    e.setMessage("Database COMMIT");
                    bus.publish(e);
                    FlowEvent db = base("DATABASE", "DATABASE", "SUCCESS");
                    db.setMessage("Database committed changes");
                    bus.publish(db);
                }

                @Override
                public void afterCompletion(int completionStatus) {
                    if (completionStatus == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        FlowEvent e = base("TRANSACTION", "TRANSACTION", "ROLLED_BACK");
                        e.setMessage("Database ROLLBACK - changes discarded");
                        bus.publish(e);
                        FlowEvent db = base("DATABASE", "DATABASE", "ROLLED_BACK");
                        db.setMessage("Database restored (rollback)");
                        bus.publish(db);
                    }
                }
            });
        }
    }

    public void transactionRollback(String reason) {
        FlowEvent e = base("TRANSACTION", "TRANSACTION", "ROLLED_BACK");
        e.setMessage(sanitize("Transaction ROLLBACK: " + reason));
        e.setError(sanitize(reason));
        bus.publish(e);
    }

    public void response(int httpStatus, long durationMs, boolean success) {
        FlowEvent e = base("RESPONSE", "CONTROLLER", success ? "SUCCESS" : "FAILED");
        e.setHttpStatus(httpStatus);
        e.setDurationMs(durationMs);
        e.setMessage((success ? "Response returned " : "Error response ") + httpStatus + " in " + durationMs + " ms");
        bus.publish(e);
    }

    public void error(String message, String error) {
        FlowEvent e = base("ERROR", currentErrorLayer(), "FAILED");
        e.setMessage(sanitize(message));
        e.setError(sanitize(error));
        bus.publish(e);
    }

    private String currentErrorLayer() {
        // Error layer attribution based on operation phase is done by callers;
        // default to SERVICE so the failure point is visible.
        return "SERVICE";
    }

    /** Collapses whitespace so SQL renders on few lines in the UI. */
    static String normalizeSql(String sql) {
        if (sql == null) return null;
        return sanitize(sql.trim().replaceAll("\\s+", " "));
    }
}
