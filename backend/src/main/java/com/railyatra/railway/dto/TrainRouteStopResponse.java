package com.railyatra.railway.dto;
import java.time.Duration; import java.time.LocalTime;
import com.railyatra.railway.domain.TrainStop;
public record TrainRouteStopResponse(String stationCode,String stationName,LocalTime arrival,LocalTime departure,long haltMinutes,int distanceKm,int dayNumber,int stopSequence){
  public static TrainRouteStopResponse from(TrainStop stop){long halt=stop.getArrivalTime()!=null&&stop.getDepartureTime()!=null?Math.max(0,Duration.between(stop.getArrivalTime(),stop.getDepartureTime()).toMinutes()):0;return new TrainRouteStopResponse(stop.getStation().getStationCode(),stop.getStation().getStationName(),stop.getArrivalTime(),stop.getDepartureTime(),halt,stop.getDistanceKm(),stop.getDayOffset()+1,stop.getStopSequence());}
}
