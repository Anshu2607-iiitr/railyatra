package com.railyatra.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PnrStatusResponse(
        String internalReference,
        String referenceLabel,
        boolean officialIndianRailwaysPnr,
        String providerPnr,
        String providerType,
        String bookingStatus,
        String currentStatus,
        Journey journey,
        List<PassengerStatus> passengers) {
    public record Journey(String trainNumber, String trainName, String origin, String destination, LocalDate journeyDate, String classCode, String quota, BigDecimal totalFare) { }
    public record PassengerStatus(int passengerNumber, String passengerName, String bookingStatus, String currentStatus, String coach, String seat, String berth) { }
}
