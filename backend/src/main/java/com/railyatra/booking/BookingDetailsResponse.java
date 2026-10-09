package com.railyatra.booking;

import java.math.BigDecimal; import java.time.*; import java.util.List;
public record BookingDetailsResponse(String internalBookingReference,String providerType,BookingStatus bookingStatus,Integer bookingStatusPosition,BookingStatus currentStatus,Integer currentStatusPosition,String trainNumber,String trainName,String origin,String destination,LocalDate journeyDate,String classCode,String quota,BigDecimal totalFare,String paymentReference,Instant bookedAt,List<Passenger> passengers){
 public record Passenger(Long id,String name,String coach,String seat,String berth,BookingStatus bookingStatus,Integer bookingStatusPosition,BookingStatus currentStatus,Integer currentStatusPosition,boolean cancelled){}
}
