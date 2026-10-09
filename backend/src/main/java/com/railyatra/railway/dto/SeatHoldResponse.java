package com.railyatra.railway.dto;

import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.SeatHoldStatus;
import java.time.Instant;
import java.time.LocalDate;

public record SeatHoldResponse(String id, String trainNumber, LocalDate journeyDate, String classCode, String quota,
                               int passengerCount, Instant createdAt, Instant expiresAt, SeatHoldStatus status) {
    public static SeatHoldResponse from(SeatHold hold) {
        return new SeatHoldResponse(hold.getId(), hold.getTrain().getTrainNumber(), hold.getJourneyDate(), hold.getTrainClass().getClassCode(), hold.getQuota(), hold.getPassengerCount(), hold.getCreatedAt(), hold.getExpiresAt(), hold.getStatus());
    }
}
