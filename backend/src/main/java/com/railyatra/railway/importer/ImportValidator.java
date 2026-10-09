package com.railyatra.railway.importer;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ImportValidator {
  public void validate(RailwayDataSet data) {
    unique(data.stations().stream().map(RailwayDataSet.StationRow::stationCode).toList(), "station code");
    unique(data.trains().stream().map(RailwayDataSet.TrainRow::trainNumber).toList(), "train number");
    Set<String> stations = new HashSet<>(data.stations().stream().map(RailwayDataSet.StationRow::stationCode).toList());
    Set<String> trains = new HashSet<>(data.trains().stream().map(RailwayDataSet.TrainRow::trainNumber).toList());
    Map<String,List<RailwayDataSet.StopRow>> routes = new HashMap<>();
    for (var stop : data.stops()) { if (!trains.contains(stop.trainNumber()) || !stations.contains(stop.stationCode())) throw new IllegalArgumentException("Route references an unknown train or station"); routes.computeIfAbsent(stop.trainNumber(), ignored -> new ArrayList<>()).add(stop); }
    for (var route : routes.values()) { route.sort(Comparator.comparingInt(RailwayDataSet.StopRow::stopSequence)); if (route.size()<2) throw new IllegalArgumentException("Each train requires at least two stops"); int lastDistance=-1; for(int i=0;i<route.size();i++){var stop=route.get(i); if(stop.stopSequence()!=i+1) throw new IllegalArgumentException("Stop order must be ascending and continuous"); if(stop.distanceKm()<=lastDistance) throw new IllegalArgumentException("Route distance must ascend"); lastDistance=stop.distanceKm();} }
    Set<String> scheduled = new HashSet<>();
    for (var schedule : data.schedules()) { if (!trains.contains(schedule.trainNumber()) || schedule.runsOn()==null || !schedule.runsOn().matches(".*1.*")) throw new IllegalArgumentException("Each scheduled train needs at least one running day"); scheduled.add(schedule.trainNumber()); }
    if (!scheduled.containsAll(trains)) throw new IllegalArgumentException("Every train needs at least one running-day schedule");
    for (var fare : data.fares()) if (!stations.contains(fare.originCode()) || !stations.contains(fare.destinationCode()) || fare.originCode().equals(fare.destinationCode())) throw new IllegalArgumentException("Fare source and destination must be valid and different");
  }
  private void unique(List<String> values,String label){ if(values.stream().anyMatch(value -> value==null || value.isBlank()) || new HashSet<>(values).size()!=values.size()) throw new IllegalArgumentException("Duplicate or blank "+label); }
}
