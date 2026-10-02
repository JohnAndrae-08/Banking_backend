package com.example.banking.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class BankingIds {
    private static final SecureRandom RANDOM = new SecureRandom();

    private BankingIds() {
    }

    /** Generates a unique 10-digit account number starting with '1'. */
    public static String generateAccountNumber() {
        long min = 1_000_000_000L;
        long max = 1_999_999_999L;
        long n = min + (Math.abs(RANDOM.nextLong()) % (max - min + 1));
        return Long.toString(n);
    }

    /** Generates a unique immutable transaction reference like TXN-ABC123XYZ789. */
    public static String generateReferenceNumber() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
