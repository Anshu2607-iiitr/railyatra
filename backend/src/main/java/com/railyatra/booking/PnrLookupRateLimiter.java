package com.railyatra.booking;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Local-development limiter. Production deployments should replace this with a shared Redis-backed implementation. */
@Component
public class PnrLookupRateLimiter {
    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final int windowSeconds;

    public PnrLookupRateLimiter(@Value("${railyatra.pnr.lookup-max-per-window:10}") int maximumAttempts,
                                @Value("${railyatra.pnr.lookup-window-seconds:60}") int windowSeconds) {
        this.maximumAttempts = maximumAttempts;
        this.windowSeconds = windowSeconds;
    }

    public void check(String clientKey) {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        Deque<Instant> window = attempts.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) window.removeFirst();
            if (window.size() >= maximumAttempts) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many PNR lookup attempts. Please try again shortly.");
            window.addLast(Instant.now());
        }
    }
}
