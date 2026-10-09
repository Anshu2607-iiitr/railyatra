package com.railyatra.railway.domain;

import jakarta.persistence.*;

@Entity @Table(name="train_classes")
public class TrainClass {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train;
  @Column(name="class_code", nullable=false, length=8) private String classCode;
  @Column(name="class_name", nullable=false) private String className;
  @Column(nullable=false) private boolean active=true;
  protected TrainClass() { }
  public TrainClass(Train train,String code,String name){this.train=train;this.classCode=code;this.className=name;}
  public Long getId(){return id;} public Train getTrain(){return train;} public String getClassCode(){return classCode;} public String getClassName(){return className;}
  public boolean isActive(){return active;} public void setActive(boolean active){this.active=active;}
}
