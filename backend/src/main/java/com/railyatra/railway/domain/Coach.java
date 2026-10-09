package com.railyatra.railway.domain;

import jakarta.persistence.*;

@Entity @Table(name="coaches")
public class Coach {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="train_class_id") private TrainClass trainClass;
  @ManyToOne @JoinColumn(name="layout_template_id") private CoachLayoutTemplate layoutTemplate;
  @Column(name="coach_code", nullable=false) private String coachCode;
  @Column(name="coach_position", nullable=false) private int coachPosition;
  protected Coach() { }
  public Coach(TrainClass trainClass,String code,int position){this.trainClass=trainClass;this.coachCode=code;this.coachPosition=position;}
  public Coach(TrainClass trainClass, CoachLayoutTemplate layoutTemplate, String code, int position){this(trainClass,code,position);this.layoutTemplate=layoutTemplate;}
  public Long getId(){return id;} public TrainClass getTrainClass(){return trainClass;} public CoachLayoutTemplate getLayoutTemplate(){return layoutTemplate;} public String getCoachCode(){return coachCode;} public int getCoachPosition(){return coachPosition;}
}
