package com.railyatra.admin;
import java.time.LocalDate;
public record AdminInventoryResponse(
 Long id,String trainNumber,String trainName,LocalDate journeyDate,String classCode,String quota,long version,
 int physicalCapacity,int totalCapacity,int available,int confirmed,int held,int confirmedHeld,int racHeld,
 int waitlistHolds,int racCapacity,int racAvailable,int rac,int waitingList,int confirmedReserved,int racReserved) {}
