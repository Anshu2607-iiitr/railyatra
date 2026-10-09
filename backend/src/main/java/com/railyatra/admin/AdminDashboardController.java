package com.railyatra.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Both endpoints are protected by SecurityConfig's /api/admin/** ROLE_ADMIN rule. */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;
    private final AdminDashboardEvents events;

    public AdminDashboardController(AdminDashboardService dashboard, AdminDashboardEvents events) {
        this.dashboard = dashboard; this.events = events;
    }

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> snapshot() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dashboard.snapshot());
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> events() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Accel-Buffering", "no").body(events.subscribe());
    }
}
