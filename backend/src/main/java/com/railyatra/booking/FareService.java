package com.railyatra.booking;
import com.railyatra.railway.domain.*; import com.railyatra.railway.repository.*; import java.math.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
@Service public class FareService {
 private final FareRuleRepository fares; private final TrainStopRepository stops; private final StationRepository stations;
 public FareService(FareRuleRepository fares, TrainStopRepository stops, StationRepository stations){this.fares=fares;this.stops=stops;this.stations=stations;}
 public FareQuote quote(Train train, TrainClass trainClass, String originCode, String destinationCode) {
  String origin=originCode.trim().toUpperCase(), destination=destinationCode.trim().toUpperCase();
  if(origin.equals(destination)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Origin and destination must be different");
  Station originStation=stations.findByStationCode(origin).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Origin station is invalid"));
  Station destinationStation=stations.findByStationCode(destination).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Destination station is invalid"));
  TrainStop from=stops.findByTrainIdAndStationStationCode(train.getId(),origin).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Train does not stop at origin"));
  TrainStop to=stops.findByTrainIdAndStationStationCode(train.getId(),destination).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Train does not stop at destination"));
  if(from.getStopSequence()>=to.getStopSequence()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Destination must be after origin on this train");
  FareRule rule=fares.findByTrainIdAndTrainClassIdAndOriginStationStationCodeAndDestinationStationStationCodeAndActiveTrue(train.getId(),trainClass.getId(),origin,destination)
    .or(()->fares.findFirstByTrainIdAndTrainClassIdAndActiveTrue(train.getId(),trainClass.getId()))
    .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"No active fare rule exists for this train class"));
  int km=to.getDistanceKm()-from.getDistanceKm(); BigDecimal calculated=rule.getBaseFare().add(rule.getPerKmFare().multiply(BigDecimal.valueOf(km))).max(rule.getMinimumFare()).setScale(2,RoundingMode.HALF_UP);
  return new FareQuote(originStation,destinationStation,calculated);
 }
 public record FareQuote(Station origin, Station destination, BigDecimal totalFare){}
}
