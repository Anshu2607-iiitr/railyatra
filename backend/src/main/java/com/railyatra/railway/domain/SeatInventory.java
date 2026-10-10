package com.railyatra.railway.domain;
import java.time.LocalDate; import jakarta.persistence.*;
@Entity @Table(name="seat_inventories") public class SeatInventory {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train; @ManyToOne(optional=false) @JoinColumn(name="train_class_id") private TrainClass trainClass; @Column(name="journey_date",nullable=false) private LocalDate journeyDate; @Column(nullable=false) private String quota; @Column(name="total_capacity",nullable=false) private int totalCapacity; @Column(name="available_count",nullable=false) private int availableCount; @Column(name="rac_capacity",nullable=false) private int racCapacity; @Column(name="rac_available_count",nullable=false) private int racAvailableCount; @Column(name="waiting_list_count",nullable=false) private int waitingListCount; @Version private long version;
 protected SeatInventory(){} public SeatInventory(Train train,TrainClass trainClass,LocalDate date,String quota,int capacity,int rac){this.train=train;this.trainClass=trainClass;this.journeyDate=date;this.quota=quota;this.totalCapacity=capacity;this.availableCount=capacity;this.racCapacity=rac;this.racAvailableCount=rac;}
 public int getTotalCapacity(){return totalCapacity;} public int getAvailableCount(){return availableCount;} public int getRacAvailableCount(){return racAvailableCount;} public int getWaitingListCount(){return waitingListCount;}
 public long getVersion(){return version;} public int getRacCapacity(){return racCapacity;}
 public void adjustCapacities(int confirmedCapacity,int newRacCapacity){
  int confirmedReserved=totalCapacity-availableCount, racReserved=racCapacity-racAvailableCount;
  if(confirmedCapacity<confirmedReserved||newRacCapacity<racReserved||confirmedCapacity<0||newRacCapacity<0)
   throw new IllegalArgumentException("Capacity cannot be below reserved seats");
  totalCapacity=confirmedCapacity;availableCount=confirmedCapacity-confirmedReserved;
  racCapacity=newRacCapacity;racAvailableCount=newRacCapacity-racReserved;
 }
 public Long getId(){return id;} public TrainClass getTrainClass(){return trainClass;} public Train getTrain(){return train;} public String getQuota(){return quota;} public LocalDate getJourneyDate(){return journeyDate;}
 public void reserveConfirmedSeats(int count){if(count<1||availableCount<count)throw new IllegalStateException("Insufficient confirmed inventory");availableCount-=count;}
 public void releaseConfirmedSeats(int count){if(count<1)throw new IllegalArgumentException("Release count must be positive");availableCount=Math.min(totalCapacity,availableCount+count);}
 public void reserveHoldSeats(int count){if(count<1||availableCount<count)throw new IllegalStateException("Insufficient confirmed inventory");availableCount-=count;}
 public void releaseHoldSeats(int count){releaseConfirmedSeats(count);}
 public void reserveRacSeats(int count){if(count<1||racAvailableCount<count)throw new IllegalStateException("Insufficient RAC inventory");racAvailableCount-=count;}
 public void releaseRacSeats(int count){if(count<1)throw new IllegalArgumentException("Release count must be positive");racAvailableCount=Math.min(racCapacity,racAvailableCount+count);}
 public void addWaitingList(int count){if(count<1)throw new IllegalArgumentException("Count must be positive");waitingListCount+=count;}
 public void removeWaitingList(int count){if(count<1||waitingListCount<count)throw new IllegalStateException("Invalid waiting-list release");waitingListCount-=count;}
}
