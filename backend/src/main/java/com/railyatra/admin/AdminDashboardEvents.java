package com.railyatra.admin;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AdminDashboardEvents {
    private static final Logger log = LoggerFactory.getLogger(AdminDashboardEvents.class);
    private final AdminDashboardService dashboard;
    private final Semaphore slots = new Semaphore(32);
    private final ConcurrentHashMap<SseEmitter, Subscription> subscribers = new ConcurrentHashMap<>();

    public AdminDashboardEvents(AdminDashboardService dashboard) { this.dashboard = dashboard; }

    public SseEmitter subscribe() {
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many dashboard connections. Try again shortly.");
        SseEmitter emitter = new SseEmitter(60_000L);
        Subscription subscription = new Subscription();
        Runnable remove = () -> {
            if (subscription.closed.compareAndSet(false, true)) {
                subscribers.remove(emitter); slots.release();
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> { remove.run(); emitter.complete(); });
        emitter.onError(error -> remove.run());
        try {
            // Database read precedes connected: failures don't masquerade as a healthy stream.
            subscription.last = dashboard.snapshot();
            emitter.send(SseEmitter.event().name("connected").data(Map.of("refreshRequired", true)));
            subscribers.put(emitter, subscription);
            if (subscription.closed.get()) subscribers.remove(emitter);
        } catch (IOException | IllegalStateException exception) {
            remove.run(); emitter.complete();
        } catch (RuntimeException exception) {
            remove.run(); emitter.complete(); throw exception;
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${railyatra.admin.dashboard-poll-ms:5000}")
    public void refresh() {
        if (subscribers.isEmpty()) return;
        final AdminDashboardResponse current;
        try { current = dashboard.snapshot(); }
        catch (RuntimeException exception) {
            log.error("Admin dashboard database refresh failed", exception);
            // Disconnect so clients mark the view stale and retry. Never send invented values.
            subscribers.forEach((emitter, subscription) -> emitter.completeWithError(exception));
            return;
        }
        subscribers.forEach((emitter, subscription) -> {
            try {
                if (!current.equals(subscription.last)) {
                    emitter.send(SseEmitter.event().name("dashboard-change").data(Map.of("refreshRequired", true)));
                    subscription.last = current;
                } else {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
            } catch (IOException | IllegalStateException exception) { emitter.complete(); }
        });
    }

    private static final class Subscription {
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile AdminDashboardResponse last;
    }
}
