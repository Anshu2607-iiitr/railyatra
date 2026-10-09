package com.railyatra.railway.service;

import com.railyatra.passenger.BerthPreference;
import com.railyatra.railway.domain.SeatType;
import java.util.List;

public record SeatAllocationResult(String allocationReference, List<AssignedSeat> assignments) {
    public record AssignedSeat(int passengerIndex, BerthPreference requestedBerth, String coachCode, String seatNumber, SeatType seatType, boolean preferenceHonoured) { }
}
