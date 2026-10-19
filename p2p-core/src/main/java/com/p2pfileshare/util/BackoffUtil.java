package com.p2pfileshare.util;

public final class BackoffUtil {
    private BackoffUtil() {
    }

    public static long computeDelay(int attempt) {
        long base = 500L;
        long delay = base * (1L << Math.min(attempt, 6));
        return Math.min(delay, 15000L);
    }
}
