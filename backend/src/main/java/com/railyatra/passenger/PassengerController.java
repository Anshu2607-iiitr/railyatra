package com.railyatra.passenger;

import com.railyatra.auth.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PassengerController {
    private final PassengerService passengerService;
    public PassengerController(PassengerService passengerService) { this.passengerService = passengerService; }

    @GetMapping("/saved-passengers")
    public List<SavedPassengerResponse> list(Authentication authentication) { return passengerService.list(principal(authentication)); }
    @PostMapping("/saved-passengers") @ResponseStatus(HttpStatus.CREATED)
    public SavedPassengerResponse create(Authentication authentication, @Valid @RequestBody PassengerDetailsRequest request) { return passengerService.create(principal(authentication), request); }
    @PutMapping("/saved-passengers/{id}")
    public SavedPassengerResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody PassengerDetailsRequest request) { return passengerService.update(principal(authentication), id, request); }
    @DeleteMapping("/saved-passengers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) { passengerService.delete(principal(authentication), id); }

    @PostMapping("/passengers/validate")
    public List<PassengerDetailsRequest> validate(@Valid @RequestBody PassengerBatchRequest request) { return passengerService.validate(request); }
    private AuthPrincipal principal(Authentication authentication) { return (AuthPrincipal) authentication.getPrincipal(); }
}
