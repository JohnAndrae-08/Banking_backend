package com.example.banking.monitor;

import java.time.Instant;
import java.util.List;

/**
 * Structured event emitted as the request travels through backend layers.
 * Serialized to JSON and pushed to the browser over SSE.
 * Never contains passwords, hashes, JWT secrets, or full tokens.
 */
public class FlowEvent {
    private String requestId;
    private Instant timestamp;
    /** Event type: REQUEST, CONTROLLER, SERVICE, REPOSITORY, SQL, DATABASE, TRANSACTION, AUTH, RESPONSE, ERROR */
    private String type;
    /** Layer highlighted in the UI: CLIENT, AUTH, CONTROLLER, SERVICE, REPOSITORY, SQL, DATABASE, TRANSACTION */
    private String layer;
    /** Business operation: REGISTER, LOGIN, CREATE_ACCOUNT, BALANCE, DEPOSIT, WITHDRAW, TRANSFER, HISTORY, etc. */
    private String operation;
    private String method;
    private String endpoint;
    /** Node state: RUNNING, SUCCESS, FAILED, ROLLED_BACK */
    private String status;
    private Integer httpStatus;
    private Long durationMs;
    private String message;
    private String sql;
    private List<String> tables;
    private Boolean authenticated;
    private String user;
    private String error;

    public FlowEvent() {
        this.timestamp = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final FlowEvent e = new FlowEvent();

        public Builder requestId(String v) { e.requestId = v; return this; }
        public Builder type(String v) { e.type = v; return this; }
        public Builder layer(String v) { e.layer = v; return this; }
        public Builder operation(String v) { e.operation = v; return this; }
        public Builder method(String v) { e.method = v; return this; }
        public Builder endpoint(String v) { e.endpoint = v; return this; }
        public Builder status(String v) { e.status = v; return this; }
        public Builder httpStatus(Integer v) { e.httpStatus = v; return this; }
        public Builder durationMs(Long v) { e.durationMs = v; return this; }
        public Builder message(String v) { e.message = v; return this; }
        public Builder sql(String v) { e.sql = v; return this; }
        public Builder tables(List<String> v) { e.tables = v; return this; }
        public Builder authenticated(Boolean v) { e.authenticated = v; return this; }
        public Builder user(String v) { e.user = v; return this; }
        public Builder error(String v) { e.error = v; return this; }
        public FlowEvent build() {
            if (e.timestamp == null) e.timestamp = Instant.now();
            return e;
        }
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public List<String> getTables() { return tables; }
    public void setTables(List<String> tables) { this.tables = tables; }
    public Boolean getAuthenticated() { return authenticated; }
    public void setAuthenticated(Boolean authenticated) { this.authenticated = authenticated; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
