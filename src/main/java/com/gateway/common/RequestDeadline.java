package com.gateway.common;

import java.util.concurrent.TimeUnit;

/** Monotonic deadline shared by request admission and provider dispatch; never sourced from client headers. */
public final class RequestDeadline {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private RequestDeadline() {}
    public static void start(int timeoutMs) {
        DEADLINE.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs));
    }
    public static long remainingMillis(int fallbackMs) {
        Long deadline = DEADLINE.get();
        return deadline == null ? fallbackMs : Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
    }
    public static void clear() { DEADLINE.remove(); }
}
