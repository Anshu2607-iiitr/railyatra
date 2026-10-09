package com.railyatra.payment;
import com.railyatra.auth.AuthPrincipal; import jakarta.validation.Valid; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/payments") public class PaymentController {
 private final PaymentService service; public PaymentController(PaymentService service){this.service=service;}
 @PostMapping public PaymentResponse create(@AuthenticationPrincipal AuthPrincipal principal,@Valid @RequestBody CreatePaymentRequest request){return service.create(principal,request);}
}
