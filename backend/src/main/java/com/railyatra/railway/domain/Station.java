package com.railyatra.railway.domain;

import jakarta.persistence.*;

@Entity @Table(name = "stations")
public class Station {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "station_code", nullable = false, unique = true, length = 10) private String stationCode;
  @Column(name = "station_name", nullable = false) private String stationName;
  @Column(nullable = false) private String city;
  private String state;
  @Column(nullable = false) private boolean active = true;
  @Column(name = "data_source", nullable = false, length = 20) private String dataSource = "SYNTHETIC";
  protected Station() { }
  public Station(String code, String name, String city, String state) { this.stationCode=code; this.stationName=name; this.city=city; this.state=state; }
  public Long getId(){return id;} public String getStationCode(){return stationCode;} public String getStationName(){return stationName;} public String getCity(){return city;} public String getState(){return state;} public boolean isActive(){return active;}
  public String getDataSource(){return dataSource;}
}
