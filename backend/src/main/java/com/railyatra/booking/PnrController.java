package com.railyatra.booking;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/pnr")
public class PnrController {
    private final PnrService service;
    private final PnrLookupRateLimiter limiter;
    private final BookingStatusEventPublisher statusEvents;
    public PnrController(PnrService service, PnrLookupRateLimiter limiter, BookingStatusEventPublisher statusEvents) { this.service = service; this.limiter = limiter; this.statusEvents = statusEvents; }

    @GetMapping("/{reference}")
    public PnrStatusResponse status(@PathVariable String reference, HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        return service.find(reference);
    }
    @GetMapping(value="/{reference}/events",produces="text/event-stream")
    public org.springframework.http.ResponseEntity<SseEmitter> events(@PathVariable String reference,@org.springframework.security.core.annotation.AuthenticationPrincipal com.railyatra.auth.AuthPrincipal user) {
        service.requireOwner(user,reference);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("X-Accel-Buffering","no").body(statusEvents.subscribe(user.id(),reference.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}
