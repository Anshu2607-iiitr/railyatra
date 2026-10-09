package com.railyatra.railway.domain;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity @Table(name="fare_rules")
public class FareRule {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train;
  @ManyToOne(optional=false) @JoinColumn(name="train_class_id") private TrainClass trainClass;
  @ManyToOne(optional=false) @JoinColumn(name="origin_station_id") private Station originStation;
  @ManyToOne(optional=false) @JoinColumn(name="destination_station_id") private Station destinationStation;
  @Column(name="base_fare", nullable=false, precision=12, scale=2) private BigDecimal baseFare;
  @Column(name="per_km_fare", nullable=false, precision=12, scale=4) private BigDecimal perKmFare;
  @Column(name="minimum_fare", nullable=false, precision=12, scale=2) private BigDecimal minimumFare;
  @Column(nullable=false) private boolean active=true;
  protected FareRule() { }
  public FareRule(Train train,TrainClass trainClass,Station origin,Station destination,BigDecimal base,BigDecimal perKm,BigDecimal min){this.train=train;this.trainClass=trainClass;this.originStation=origin;this.destinationStation=destination;this.baseFare=base;this.perKmFare=perKm;this.minimumFare=min;}
  public Long getId(){return id;}
  public Train getTrain(){return train;} public TrainClass getTrainClass(){return trainClass;}
  public Station getOriginStation(){return originStation;} public Station getDestinationStation(){return destinationStation;}
  public BigDecimal getBaseFare(){return baseFare;} public BigDecimal getPerKmFare(){return perKmFare;}
  public BigDecimal getMinimumFare(){return minimumFare;} public boolean isActive(){return active;}
}
