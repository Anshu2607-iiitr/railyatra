package com.railyatra.booking;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="cancellation_quotes")
public class CancellationQuote {
 @Id private String id;
 @ManyToOne(optional=false) @JoinColumn(name="booking_id") private Booking booking;
 @Column(name="passenger_ids",nullable=false) private String passengerIds;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal charge;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal refund;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="confirmed_at") private Instant confirmedAt;
 @Column(name="refund_status",nullable=false) private String refundStatus;
 @Version private long version;
 @Column(name="processing_at") private Instant processingAt;
 @Column(name="processing_token") private String processingToken;
 @Column(name="refund_attempts",nullable=false) private int refundAttempts;
 @Column(name="refund_failure") private String refundFailure;
 protected CancellationQuote(){}
 public CancellationQuote(Booking booking,String passengerIds,BigDecimal charge,BigDecimal refund,Instant expiry){id=UUID.randomUUID().toString();this.booking=booking;this.passengerIds=passengerIds;this.charge=charge;this.refund=refund;expiresAt=expiry;refundStatus="NOT_REQUESTED";}
 public void confirm(){confirmedAt=Instant.now();refundStatus=refund.signum()>0?"REFUND_PENDING":"NOT_APPLICABLE";}
 public void processing(String token){processingToken=token;processingAt=Instant.now();refundAttempts++;refundStatus="REFUND_PROCESSING";refundFailure=null;}
 public void refunded(){refundStatus="REFUNDED";processingToken=null;processingAt=null;refundFailure=null;}
 public void failed(String reason){refundStatus="REFUND_FAILED";processingToken=null;processingAt=null;refundFailure=reason;}
 public void retry(){refundStatus="REFUND_PENDING";refundFailure=null;}
 public Instant getProcessingAt(){return processingAt;} public String getProcessingToken(){return processingToken;} public int getRefundAttempts(){return refundAttempts;} public String getRefundFailure(){return refundFailure;}
 public String getId(){return id;} public Booking getBooking(){return booking;} public String getPassengerIds(){return passengerIds;} public BigDecimal getCharge(){return charge;} public BigDecimal getRefund(){return refund;} public Instant getExpiresAt(){return expiresAt;} public Instant getConfirmedAt(){return confirmedAt;} public String getRefundStatus(){return refundStatus;}
}
