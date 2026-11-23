package com.ershak.security;

import com.ershak.exception.BankException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter per key (for example per IP address on the login endpoint).
 * Slows down password-guessing bots before the account lockout even kicks in.
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowMillis) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public void check(String key, long now) {
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > windowMillis) {
                times.pollFirst();
            }
            if (times.size() >= maxRequests) {
                throw new BankException("RATE_LIMITED", "Too many requests. Wait a minute and try again.", 429);
            }
            times.addLast(now);
        }
    }
}
