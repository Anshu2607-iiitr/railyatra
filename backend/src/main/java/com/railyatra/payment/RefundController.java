package com.railyatra.payment;
import com.railyatra.auth.AuthPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class RefundController {
 private final RefundService refunds;public RefundController(RefundService refunds){this.refunds=refunds;}
 @GetMapping("/api/bookings/{reference}/refunds") public List<RefundService.RefundView> history(@AuthenticationPrincipal AuthPrincipal user,@PathVariable String reference){return refunds.history(user,reference);}
 @PostMapping("/api/admin/refunds/{id}/retry") public void retry(@PathVariable String id){refunds.retry(id);}
}
