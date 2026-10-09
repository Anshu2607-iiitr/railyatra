package com.railyatra.booking;
import com.railyatra.auth.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/bookings/{reference}")
public class CancellationController {
 private final CancellationService service;
 public CancellationController(CancellationService service){this.service=service;}
 @GetMapping("/cancellations") public List<CancellationService.QuoteResponse> history(@AuthenticationPrincipal AuthPrincipal user,@PathVariable String reference){return service.history(user,reference);}
 @PostMapping("/cancellation-quote") public CancellationService.QuoteResponse quote(@AuthenticationPrincipal AuthPrincipal user,@PathVariable String reference,@Valid @RequestBody Selection selection){return service.quote(user,reference,selection.passengerIds());}
 @PostMapping("/cancel") public CancellationService.QuoteResponse confirm(@AuthenticationPrincipal AuthPrincipal user,@PathVariable String reference,@Valid @RequestBody Confirmation request){return service.confirm(user,reference,request.quoteId());}
 public record Selection(@NotEmpty @Size(max=6) List<@NotNull @Positive Long> passengerIds){}
 public record Confirmation(@NotBlank @Size(max=36) String quoteId){}
}
