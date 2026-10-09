package com.railyatra.booking;
import com.railyatra.passenger.*; import java.time.*; import java.util.List;
public record CheckoutReviewResponse(String trainNumber,String trainName,String origin,String destination,LocalDate journeyDate,LocalTime departure,LocalTime arrival,String classCode,String quota,List<Passenger> passengers,FareResponse fare,Instant holdExpiresAt){
 public record Passenger(String name,int age,Gender gender,BerthPreference berthPreference){}
}
