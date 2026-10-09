package com.railyatra.railway.importer;

import java.math.BigDecimal;
import java.util.List;

public record RailwayDataSet(List<StationRow> stations, List<TrainRow> trains, List<StopRow> stops, List<ScheduleRow> schedules, List<ClassRow> classes, List<CoachRow> coaches, List<SeatRow> seats, List<FareRow> fares) {
  public record StationRow(String stationCode, String stationName, String city, String state, String dataSource) { }
  public record TrainRow(String trainNumber, String trainName, String dataSource) { }
  public record StopRow(String trainNumber, String stationCode, int stopSequence, String arrivalTime, String departureTime, int dayOffset, int distanceKm) { }
  public record ScheduleRow(String trainNumber, String runsOn, String effectiveFrom) { }
  public record ClassRow(String trainNumber, String classCode, String className) { }
  public record CoachRow(String trainNumber, String classCode, String coachCode, int coachPosition) { }
  public record SeatRow(String trainNumber, String classCode, String coachCode, String seatNumber, String berthType) { }
  public record FareRow(String trainNumber, String classCode, String originCode, String destinationCode, BigDecimal baseFare, BigDecimal perKmFare, BigDecimal minimumFare) { }
}
