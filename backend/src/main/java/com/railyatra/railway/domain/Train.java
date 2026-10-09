package com.railyatra.railway.domain;

import jakarta.persistence.*;

@Entity @Table(name = "trains")
public class Train {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "train_number", nullable = false, unique = true, length = 10) private String trainNumber;
  @Column(name = "train_name", nullable = false) private String trainName;
  @Column(nullable = false) private boolean active = true;
  @Column(name = "data_source", nullable = false, length = 20) private String dataSource = "SYNTHETIC";
  @Enumerated(EnumType.STRING) @Column(name="train_type", nullable=false, length=24) private TrainType trainType = TrainType.EXPRESS;
  @Version private long version;
  protected Train() { }
  public Train(String number, String name) { this.trainNumber=number; this.trainName=name; }
  public Long getId(){return id;} public String getTrainNumber(){return trainNumber;} public String getTrainName(){return trainName;} public boolean isActive(){return active;}
  public String getDataSource(){return dataSource;}
  public TrainType getTrainType(){return trainType;} public long getVersion(){return version;}
  public static Train adminCreated(String number, String name, TrainType type) {
    Train train = new Train(number, name); train.trainType = type; train.dataSource = "LOCAL_ADMIN"; return train;
  }
  public void edit(String number, String name, TrainType type) { trainNumber=number; trainName=name; trainType=type; }
  public void deactivate() { active=false; }
}
