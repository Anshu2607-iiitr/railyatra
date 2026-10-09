package com.railyatra.railway.dto;
import java.time.LocalTime;
import com.railyatra.railway.repository.TrainSearchProjection;
public record TrainSearchResponse(String trainNumber,String trainName,String from,String fromName,String to,String toName,LocalTime departure,LocalTime arrival,String travelClass,String quota,boolean syntheticData,String trainType,String runningDays) {
  public static TrainSearchResponse from(TrainSearchProjection row,String travelClass,String quota){return new TrainSearchResponse(row.getTrainNumber(),row.getTrainName(),row.getOriginCode(),row.getOriginName(),row.getDestinationCode(),row.getDestinationName(),row.getDepartureTime(),row.getArrivalTime(),travelClass,quota,"SYNTHETIC".equals(row.getDataSource()),row.getTrainType(),row.getRunningDays());}
}
