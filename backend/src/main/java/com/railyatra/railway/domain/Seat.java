package com.railyatra.railway.domain;

import jakarta.persistence.*;

@Entity @Table(name="seats")
public class Seat {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @ManyToOne(optional=false) @JoinColumn(name="coach_id") private Coach coach;
  @Column(name="seat_number", nullable=false) private String seatNumber;
  @Enumerated(EnumType.STRING) @Column(name="berth_type", nullable=false) private SeatType berthType;
  @Column(nullable=false) private boolean active=true;
  protected Seat() { }
  public Seat(Coach coach,String number,SeatType berthType){this.coach=coach;this.seatNumber=number;this.berthType=berthType;}
  public Long getId(){return id;} public Coach getCoach(){return coach;} public String getSeatNumber(){return seatNumber;} public SeatType getBerthType(){return berthType;}
}
