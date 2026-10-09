package com.railyatra.railway.seed;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.repository.*;

@Component
@org.springframework.core.annotation.Order(0)
@ConditionalOnProperty(prefix = "railyatra.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevelopmentRailwaySeed implements CommandLineRunner {
  private final StationRepository stations; private final TrainRepository trains; private final TrainStopRepository stops;
  private final TrainScheduleRepository schedules; private final TrainClassRepository trainClasses;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  public DevelopmentRailwaySeed(StationRepository stations, TrainRepository trains, TrainStopRepository stops, TrainScheduleRepository schedules, TrainClassRepository trainClasses, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.stations=stations;this.trains=trains;this.stops=stops;this.schedules=schedules;this.trainClasses=trainClasses;this.jdbc=jdbc; }
  @Override @Transactional public void run(String... args) {
    if(jdbc.queryForObject("SELECT completed_at FROM development_seed_runs WHERE seed_key='foundation-v1' FOR UPDATE",java.sql.Timestamp.class)!=null) return;
    Map<String, Station> route = new LinkedHashMap<>();
    route.put("RNC", station("RNC", "Ranchi Junction", "Ranchi", "Jharkhand")); route.put("MURI", station("MURI", "Muri Junction", "Silli", "Jharkhand"));
    route.put("BKSC", station("BKSC", "Bokaro Steel City", "Bokaro", "Jharkhand")); route.put("GAYA", station("GAYA", "Gaya Junction", "Gaya", "Bihar"));
    route.put("PRYJ", station("PRYJ", "Prayagraj Junction", "Prayagraj", "Uttar Pradesh")); route.put("CNB", station("CNB", "Kanpur Central", "Kanpur", "Uttar Pradesh"));
    route.put("NDLS", station("NDLS", "New Delhi", "New Delhi", "Delhi"));
    seedForward(route); seedReverse(route);
    jdbc.update("UPDATE development_seed_runs SET completed_at=CURRENT_TIMESTAMP WHERE seed_key='foundation-v1'");
  }
  private Station station(String code,String name,String city,String state) { return stations.findByStationCode(code).orElseGet(() -> stations.save(new Station(code,name,city,state))); }
  private void seedForward(Map<String,Station> route) {
    Train train = trains.findByTrainNumber("18625").orElseGet(() -> trains.save(new Train("18625", "Ranchi–New Delhi Development Express")));
    addStops(train, route, false); scheduleAndClass(train);
  }
  private void seedReverse(Map<String,Station> route) {
    Train train = trains.findByTrainNumber("18626").orElseGet(() -> trains.save(new Train("18626", "New Delhi–Ranchi Development Express")));
    addStops(train, route, true); scheduleAndClass(train);
  }
  private void addStops(Train train, Map<String,Station> route, boolean reverse) {
    String[] codes = route.keySet().toArray(String[]::new); int total=codes.length;
    for (int i=0;i<total;i++) { int sourceIndex=reverse ? total-1-i : i; Station station=route.get(codes[sourceIndex]);
      if (!stops.existsByTrainIdAndStationId(train.getId(), station.getId())) { LocalTime time=LocalTime.of((6+i*3)%24, i==0?0:10); stops.save(new TrainStop(train,station,i+1,i==0?null:time,i==total-1?null:time.plusMinutes(5),i/6,i*180)); }
    }
  }
  private void scheduleAndClass(Train train) {
    if (!schedules.existsByTrainIdAndEffectiveFrom(train.getId(), LocalDate.of(2026,1,1))) schedules.save(new TrainSchedule(train,"1111111",LocalDate.of(2026,1,1),null));
    if (!trainClasses.existsByTrainIdAndClassCode(train.getId(),"3A")) trainClasses.save(new TrainClass(train,"3A","AC 3 Tier"));
    if (!trainClasses.existsByTrainIdAndClassCode(train.getId(),"SL")) trainClasses.save(new TrainClass(train,"SL","Sleeper"));
  }
}
