package com.railyatra;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.railyatra.railway.domain.Station;
import com.railyatra.railway.domain.Train;
import com.railyatra.railway.domain.TrainStop;
import com.railyatra.railway.repository.StationRepository;
import com.railyatra.railway.repository.TrainRepository;
import com.railyatra.railway.service.TrainRouteService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RailwayDatabaseInitializationTest {
  @Autowired StationRepository stations;
  @Autowired TrainRepository trains;
  @Autowired TrainRouteService routeService;

  @Test @Transactional
  void stationCodesAreUnique() {
    stations.saveAndFlush(new Station("TST", "Test Station", "Test City", "Test State"));
    assertThrows(DataIntegrityViolationException.class, () -> stations.saveAndFlush(new Station("TST", "Another Test Station", "Test City", "Test State")));
  }

  @Test @Transactional
  void trainNumbersAreUnique() {
    trains.saveAndFlush(new Train("99999", "Test Express"));
    assertThrows(DataIntegrityViolationException.class, () -> trains.saveAndFlush(new Train("99999", "Another Test Express")));
  }

  @Test
  void developmentRouteIsContinuousAndOrdered() {
    Train train = trains.findByTrainNumber("18625").orElseThrow();
    List<TrainStop> stops = routeService.orderedStops(train.getId());
    assertEquals(List.of("RNC", "MURI", "BKSC", "GAYA", "PRYJ", "CNB", "NDLS"), stops.stream().map(stop -> stop.getStation().getStationCode()).toList());
    assertEquals(1, stops.getFirst().getStopSequence());
    assertEquals(stops.size(), stops.getLast().getStopSequence());
  }

  @Test
  void routeSourceAndDestinationMatchTheDeclaredDirection() {
    Train forward = trains.findByTrainNumber("18625").orElseThrow();
    List<TrainStop> forwardStops = routeService.orderedStops(forward.getId());
    assertEquals("RNC", forwardStops.getFirst().getStation().getStationCode());
    assertEquals("NDLS", forwardStops.getLast().getStation().getStationCode());
    Train reverse = trains.findByTrainNumber("18626").orElseThrow();
    List<TrainStop> reverseStops = routeService.orderedStops(reverse.getId());
    assertEquals("NDLS", reverseStops.getFirst().getStation().getStationCode());
    assertEquals("RNC", reverseStops.getLast().getStation().getStationCode());
  }
}
