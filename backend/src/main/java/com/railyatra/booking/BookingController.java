package com.railyatra.booking;
import com.railyatra.auth.AuthPrincipal; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/bookings") public class BookingController {
 private final BookingService service; private final MyBookingsService history; public BookingController(BookingService service, MyBookingsService history){this.service=service;this.history=history;}
 @PostMapping public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal AuthPrincipal principal,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateBookingRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal,key,request));}
 @GetMapping("/mine") public MyBookingsService.Result mine(@AuthenticationPrincipal AuthPrincipal principal, @RequestParam(defaultValue="UPCOMING") String tab, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) { return history.list(principal,tab,page,size); }
 @GetMapping("/{reference}") public BookingDetailsResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String reference) { return service.getForUser(principal, reference); }
}
