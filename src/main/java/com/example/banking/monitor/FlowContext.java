package com.example.banking.monitor;

/**
 * Per-request correlation state held in a ThreadLocal for the request thread.
 * Populated by {@link RequestIdFilter}, read by {@link FlowPublisher}.
 */
public class FlowContext {
    private static final ThreadLocal<State> HOLDER = new ThreadLocal<>();

    public static void init(String requestId, String method, String endpoint) {
        State s = new State();
        s.requestId = requestId;
        s.method = method;
        s.endpoint = endpoint;
        s.startNanos = System.nanoTime();
        s.operation = deriveOperation(method, endpoint);
        HOLDER.set(s);
    }

    public static State get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static long elapsedMs() {
        State s = HOLDER.get();
        if (s == null) return 0;
        return (System.nanoTime() - s.startNanos) / 1_000_000;
    }

    /** Maps HTTP method + path to a business operation used by the UI. */
    public static String deriveOperation(String method, String endpoint) {
        if (endpoint == null) return "UNKNOWN";
        String ep = endpoint;
        String m = method == null ? "" : method.toUpperCase();
        if (ep.startsWith("/api/auth/register")) return "REGISTER";
        if (ep.startsWith("/api/auth/login")) return "LOGIN";
        if (ep.equals("/api/transfers")) return "TRANSFER";
        if (ep.matches(".*/accounts/\\d+/deposit")) return "DEPOSIT";
        if (ep.matches(".*/accounts/\\d+/withdraw")) return "WITHDRAW";
        if (ep.matches(".*/accounts/\\d+/balance")) return "BALANCE";
        if (ep.matches(".*/accounts/\\d+/transactions")) return "HISTORY";
        if (ep.matches(".*/transactions/\\d+")) return "TRANSACTION_LOOKUP";
        if (ep.equals("/api/accounts") && m.equals("POST")) return "CREATE_ACCOUNT";
        if (ep.equals("/api/accounts") && m.equals("GET")) return "LIST_ACCOUNTS";
        if (ep.matches(".*/accounts/\\d+$")) return "ACCOUNT_DETAILS";
        if (ep.startsWith("/api/customers")) return "CUSTOMER";
        if (ep.startsWith("/api/monitor")) return "MONITOR";
        return m + " " + ep;
    }

    public static class State {
        String requestId;
        String method;
        String endpoint;
        long startNanos;
        String operation;
        String user;

        public String getRequestId() { return requestId; }
        public String getMethod() { return method; }
        public String getEndpoint() { return endpoint; }
        public String getOperation() { return operation; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
    }
}
