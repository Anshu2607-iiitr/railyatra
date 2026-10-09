package com.railyatra.railway.service;

import com.railyatra.railway.domain.SeatHoldStatus;
import com.railyatra.railway.repository.SeatHoldRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SeatHoldExpiryJob {
    private final SeatHoldRepository holds;
    private final SeatHoldService service;
    public SeatHoldExpiryJob(SeatHoldRepository holds, SeatHoldService service) { this.holds = holds; this.service = service; }
    @Scheduled(fixedDelayString = "${railyatra.holds.expiry-check-ms:15000}")
    public void expireDueHolds() {
        holds.findByStatusAndExpiresAtLessThanEqual(SeatHoldStatus.ACTIVE, Instant.now()).forEach(hold -> service.expireHold(hold.getId()));
    }
}
