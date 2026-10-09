package com.railyatra.booking;

import com.railyatra.railway.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "bookings")
public class Booking {
 @Id private String id;
 @Column(name="user_id", nullable=false) private Long userId;
 @ManyToOne(optional=false) @JoinColumn(name="hold_id") private SeatHold hold;
 @ManyToOne(optional=false) @JoinColumn(name="train_id") private Train train;
 @ManyToOne(optional=false) @JoinColumn(name="train_class_id") private TrainClass trainClass;
 @ManyToOne(optional=false) @JoinColumn(name="origin_station_id") private Station originStation;
 @ManyToOne(optional=false) @JoinColumn(name="destination_station_id") private Station destinationStation;
 @Column(name="journey_date", nullable=false) private LocalDate journeyDate;
 @Column(nullable=false) private String quota;
 @Column(name="internal_booking_reference", nullable=false) private String internalBookingReference;
 @Column(name="provider_pnr") private String providerPnr;
 @Column(name="provider_type", nullable=false) private String providerType;
 @Column(name="idempotency_key", nullable=false) private String idempotencyKey;
 @Column(name="total_fare", nullable=false, precision=12, scale=2) private BigDecimal totalFare;
 @Column(name="payment_reference") private String paymentReference;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private BookingStatus status;
 @Enumerated(EnumType.STRING) @Column(name="booking_status", nullable=false) private BookingStatus bookingStatus;
 @Enumerated(EnumType.STRING) @Column(name="current_status", nullable=false) private BookingStatus currentStatus;
 @Column(name="booking_status_position") private Integer bookingStatusPosition;
 @Column(name="current_status_position") private Integer currentStatusPosition;
 @Column(name="created_at", nullable=false) private Instant createdAt;
 @Column(name="updated_at", nullable=false) private Instant updatedAt;
 @Version private long version;
 protected Booking() {}
 public Booking(Long userId, SeatHold hold, Station origin, Station destination, String reference, String idempotencyKey, BigDecimal fare) {
  this.id=UUID.randomUUID().toString(); this.userId=userId; this.hold=hold; this.train=hold.getTrain(); this.trainClass=hold.getTrainClass(); this.originStation=origin; this.destinationStation=destination; this.journeyDate=hold.getJourneyDate(); this.quota=hold.getQuota(); this.internalBookingReference=reference; this.providerType="LOCAL"; this.idempotencyKey=idempotencyKey; this.totalFare=fare; this.status=BookingStatus.PENDING_PAYMENT; this.bookingStatus=BookingStatus.PENDING_PAYMENT; this.currentStatus=BookingStatus.PENDING_PAYMENT; this.createdAt=Instant.now(); this.updatedAt=createdAt;
 }
 public void markPaid(String paymentReference, BookingStatus initialStatus, Integer position) { this.paymentReference=paymentReference; this.status=initialStatus; this.bookingStatus=initialStatus; this.currentStatus=initialStatus; this.bookingStatusPosition=position; this.currentStatusPosition=position; this.updatedAt=Instant.now(); }
 public void markFailed(String paymentReference) { this.paymentReference=paymentReference; this.status=BookingStatus.FAILED; this.bookingStatus=BookingStatus.FAILED; this.currentStatus=BookingStatus.FAILED; this.updatedAt=Instant.now(); }
 public void promoteToConfirmed() { this.status=BookingStatus.CONFIRMED; this.currentStatus=BookingStatus.CONFIRMED; this.currentStatusPosition=null; this.updatedAt=Instant.now(); }
 public void promoteToRac(Integer position) { this.status=BookingStatus.RAC; this.currentStatus=BookingStatus.RAC; this.currentStatusPosition=position; this.updatedAt=Instant.now(); }
 public void reindexCurrent(Integer position) { this.currentStatusPosition=position; this.updatedAt=Instant.now(); }
 public void cancel() { this.status=BookingStatus.CANCELLED; this.currentStatus=BookingStatus.CANCELLED; this.currentStatusPosition=null; this.updatedAt=Instant.now(); }
 public void touch(){this.updatedAt=Instant.now();}
 public String getId(){return id;} public Long getUserId(){return userId;} public SeatHold getHold(){return hold;} public Train getTrain(){return train;} public TrainClass getTrainClass(){return trainClass;} public Station getOriginStation(){return originStation;} public Station getDestinationStation(){return destinationStation;} public LocalDate getJourneyDate(){return journeyDate;} public String getQuota(){return quota;} public String getInternalBookingReference(){return internalBookingReference;} public String getProviderPnr(){return providerPnr;} public String getProviderType(){return providerType;} public BigDecimal getTotalFare(){return totalFare;} public String getPaymentReference(){return paymentReference;} public BookingStatus getStatus(){return status;} public BookingStatus getBookingStatus(){return bookingStatus;} public BookingStatus getCurrentStatus(){return currentStatus;} public Integer getBookingStatusPosition(){return bookingStatusPosition;} public Integer getCurrentStatusPosition(){return currentStatusPosition;} public Instant getCreatedAt(){return createdAt;}
}
