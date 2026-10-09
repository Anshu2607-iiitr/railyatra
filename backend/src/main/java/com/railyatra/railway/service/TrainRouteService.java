package com.railyatra.railway.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.railyatra.railway.domain.TrainStop;
import com.railyatra.railway.repository.TrainStopRepository;
import com.railyatra.railway.repository.TrainRepository;
import com.railyatra.railway.dto.TrainRouteStopResponse;

@Service
public class TrainRouteService {
  private final TrainStopRepository trainStopRepository;
  private final TrainRepository trainRepository;
  public TrainRouteService(TrainStopRepository trainStopRepository, TrainRepository trainRepository) { this.trainStopRepository = trainStopRepository; this.trainRepository=trainRepository; }
  public List<TrainStop> orderedStops(Long trainId) {
    List<TrainStop> stops = trainStopRepository.findByTrainIdOrderByStopSequenceAsc(trainId);
    if (stops.size() < 2) throw new IllegalStateException("A train route requires at least two ordered stops");
    for (int index=0; index<stops.size(); index++) if (stops.get(index).getStopSequence() != index + 1) throw new IllegalStateException("Train route stop sequence must be continuous and start at 1");
    return stops;
  }
  public List<TrainRouteStopResponse> route(String trainNumber){Long trainId=trainRepository.findByTrainNumber(trainNumber).orElseThrow(()->new java.util.NoSuchElementException("Train not found")).getId();return orderedStops(trainId).stream().map(TrainRouteStopResponse::from).toList();}
}
