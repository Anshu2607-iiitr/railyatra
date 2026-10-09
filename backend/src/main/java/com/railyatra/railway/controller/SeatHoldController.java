package com.railyatra.railway.controller;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.railway.dto.CreateSeatHoldRequest;
import com.railyatra.railway.dto.SeatHoldResponse;
import com.railyatra.railway.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seat-holds")
public class SeatHoldController {
    private final SeatHoldService holds;
    public SeatHoldController(SeatHoldService holds) { this.holds = holds; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SeatHoldResponse create(Authentication authentication, @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody CreateSeatHoldRequest request) { return holds.create(principal(authentication), requestKey, request); }
    @GetMapping("/{holdId}")
    public SeatHoldResponse get(Authentication authentication, @PathVariable String holdId) { return holds.getForUser(principal(authentication), holdId); }
    @DeleteMapping("/{holdId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(Authentication authentication, @PathVariable String holdId) { holds.releaseForUser(principal(authentication), holdId); }
    private AuthPrincipal principal(Authentication authentication) { return (AuthPrincipal) authentication.getPrincipal(); }
}
