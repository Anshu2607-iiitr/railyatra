package com.railyatra.railway.realtime;
import java.time.Instant; import java.time.LocalDate;
public record AvailabilityEvent(String trainNumber,LocalDate journeyDate,String travelClass,String quota,String reason,Instant occurredAt){ }
