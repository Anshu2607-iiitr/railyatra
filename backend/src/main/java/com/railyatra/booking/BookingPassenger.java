package com.railyatra.booking;
import com.railyatra.passenger.*;
import com.railyatra.railway.service.SeatAllocationResult;
import jakarta.persistence.*;
@Entity @Table(name="booking_passengers")
public class BookingPassenger {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="booking_id") private Booking booking;
 @Column(name="passenger_index", nullable=false) private int passengerIndex;
 @Column(name="passenger_name", nullable=false) private String passengerName;
 @Column(nullable=false) private int age;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Gender gender;
 @Enumerated(EnumType.STRING) @Column(name="berth_preference", nullable=false) private BerthPreference berthPreference;
 @Column(name="coach_code") private String coachCode; @Column(name="seat_number") private String seatNumber;
 @Column(name="berth_type") private String berthType;
 @Column(nullable=false) private boolean cancelled;
 public Long getId(){return id;} public boolean isCancelled(){return cancelled;}
 public void cancel(){cancelled=true;coachCode=null;seatNumber=null;berthType=null;}
 protected BookingPassenger() {}
 public BookingPassenger(Booking booking, int index, PassengerDetailsRequest p, SeatAllocationResult.AssignedSeat seat) { this.booking=booking; passengerIndex=index; passengerName=p.name(); age=p.age(); gender=p.gender(); berthPreference=p.berthPreference(); if(seat!=null){coachCode=seat.coachCode();seatNumber=seat.seatNumber();berthType=seat.seatType().name();} }
 public int getPassengerIndex(){return passengerIndex;} public String getPassengerName(){return passengerName;}
 public int getAge(){return age;} public Gender getGender(){return gender;}
 public String getCoachCode(){return coachCode;} public String getSeatNumber(){return seatNumber;} public String getBerthType(){return berthType;}
 public BerthPreference getBerthPreference(){return berthPreference;}
 public void assignSeat(SeatAllocationResult.AssignedSeat seat){coachCode=seat.coachCode();seatNumber=seat.seatNumber();berthType=seat.seatType().name();}
}
