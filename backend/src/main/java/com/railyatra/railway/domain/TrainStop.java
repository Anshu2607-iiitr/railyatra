package com.railyatra.railway.domain;

import java.time.LocalTime;
import jakarta.persistence.*;

@Entity @Table(name="train_stops", uniqueConstraints=@UniqueConstraint(name="uk_train_stops_train_sequence", columnNames={"train_id","stop_sequence"}))
public class TrainStop {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train;
  @ManyToOne(optional=false) @JoinColumn(name="station_id") private Station station;
  @Column(name="stop_sequence", nullable=false) private int stopSequence;
  @Column(name="arrival_time") private LocalTime arrivalTime;
  @Column(name="departure_time") private LocalTime departureTime;
  @Column(name="day_offset", nullable=false) private int dayOffset;
  @Column(name="distance_km", nullable=false) private int distanceKm;
  protected TrainStop() { }
  public TrainStop(Train train, Station station, int sequence, LocalTime arrival, LocalTime departure, int dayOffset, int distanceKm) { this.train=train;this.station=station;this.stopSequence=sequence;this.arrivalTime=arrival;this.departureTime=departure;this.dayOffset=dayOffset;this.distanceKm=distanceKm; }
  public Long getId(){return id;} public Train getTrain(){return train;} public Station getStation(){return station;} public int getStopSequence(){return stopSequence;} public LocalTime getArrivalTime(){return arrivalTime;} public LocalTime getDepartureTime(){return departureTime;} public int getDayOffset(){return dayOffset;} public int getDistanceKm(){return distanceKm;}
}
