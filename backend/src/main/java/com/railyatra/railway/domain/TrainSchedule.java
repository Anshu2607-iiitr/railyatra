package com.railyatra.railway.domain;

import java.time.LocalDate;
import jakarta.persistence.*;

@Entity @Table(name="train_schedules")
public class TrainSchedule {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train;
  @Column(name="runs_on", nullable=false, length=7) private String runsOn;
  @Column(name="effective_from", nullable=false) private LocalDate effectiveFrom;
  @Column(name="effective_to") private LocalDate effectiveTo;
  protected TrainSchedule() { }
  public TrainSchedule(Train train,String runsOn,LocalDate from,LocalDate to){this.train=train;this.runsOn=runsOn;this.effectiveFrom=from;this.effectiveTo=to;}
  public Long getId(){return id;} public Train getTrain(){return train;} public String getRunsOn(){return runsOn;} public LocalDate getEffectiveFrom(){return effectiveFrom;} public LocalDate getEffectiveTo(){return effectiveTo;}
  public void setRunsOn(String runsOn){this.runsOn=runsOn;}
}
