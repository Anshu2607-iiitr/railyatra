package com.railyatra.booking;
import com.railyatra.auth.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController @RequestMapping("/api/bookings")
public class BookingEventsController {
 private final PnrService pnr;private final BookingStatusEventPublisher events;
 public BookingEventsController(PnrService pnr,BookingStatusEventPublisher events){this.pnr=pnr;this.events=events;}
 @GetMapping(value="/events",produces="text/event-stream") public ResponseEntity<SseEmitter> events(@AuthenticationPrincipal AuthPrincipal user){return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Accel-Buffering","no").body(events.subscribe(user.id(),null));}
 @GetMapping("/{reference}/pnr") public PnrStatusResponse snapshot(@AuthenticationPrincipal AuthPrincipal user,@PathVariable String reference){return pnr.findForOwner(user,reference);}
}
