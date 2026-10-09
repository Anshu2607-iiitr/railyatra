package com.railyatra.railway.dto;
import com.railyatra.railway.domain.Station;
public record StationSearchResponse(String code, String name, String city, String state) {
  public static StationSearchResponse from(Station station) { return new StationSearchResponse(station.getStationCode(), station.getStationName(), station.getCity(), station.getState()); }
}
