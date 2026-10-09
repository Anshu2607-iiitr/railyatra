package com.railyatra.payment;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.booking.*;
import jakarta.persistence.EntityManager;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundService {
 private static final Logger log=LoggerFactory.getLogger(RefundService.class);
 private final CancellationQuoteRepository quotes; private final PaymentTransactionRepository payments;
 private final BookingRepository bookings; private final PaymentProvider provider; private final TransactionTemplate tx;
 private final EntityManager em; private final long leaseSeconds;
 private final BookingStatusEventPublisher statusEvents;
 public RefundService(CancellationQuoteRepository quotes,PaymentTransactionRepository payments,BookingRepository bookings,PaymentProvider provider,PlatformTransactionManager manager,EntityManager em,@Value("${railyatra.refunds.lease-seconds:180}") long leaseSeconds,BookingStatusEventPublisher statusEvents){this.quotes=quotes;this.payments=payments;this.bookings=bookings;this.provider=provider;this.tx=new TransactionTemplate(manager);this.em=em;this.leaseSeconds=leaseSeconds;this.statusEvents=statusEvents;if(leaseSeconds<1)throw new IllegalArgumentException("Refund lease must be positive");}
 public void publishUpdate(Booking booking){statusEvents.publishRefundAfterCommit(booking);}

 public Calculation calculate(BigDecimal paid,Instant journeyTime,Instant cancellationTime,String classCode,BookingStatus status,RefundRules rules){
  Objects.requireNonNull(paid);Objects.requireNonNull(journeyTime);Objects.requireNonNull(cancellationTime);Objects.requireNonNull(classCode);Objects.requireNonNull(status);Objects.requireNonNull(rules);
  if(paid.signum()<0||paid.scale()>2||rules.getAdvanceHours()<=rules.getNearHours()||rules.getNearHours()<0)throw new IllegalArgumentException("Invalid refund inputs or time windows");
  if(!List.of(BookingStatus.CONFIRMED,BookingStatus.RAC,BookingStatus.WAITLISTED).contains(status))throw new IllegalArgumentException("Passenger status is not refundable");
  BigDecimal minimum=rules.getClassCharges().getOrDefault(classCode.toUpperCase(Locale.ROOT),rules.getConfirmedCharge());
  for(BigDecimal value:List.of(minimum,rules.getRacCharge(),rules.getWaitlistCharge(),rules.getIntermediateRate(),rules.getNearRate()))if(value.signum()<0)throw new IllegalArgumentException("Refund deductions cannot be negative");
  if(rules.getIntermediateRate().compareTo(BigDecimal.ONE)>0||rules.getNearRate().compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Refund rates must be 0 to 1");
  Duration remaining=Duration.between(cancellationTime,journeyTime);
  BigDecimal deduction;
  if(remaining.isNegative()||remaining.isZero())deduction=paid;
  else if(status==BookingStatus.WAITLISTED)deduction=rules.getWaitlistCharge();
  else if(status==BookingStatus.RAC)deduction=rules.getRacCharge();
  else if(status==BookingStatus.CONFIRMED){
   deduction=minimum;
   if(remaining.compareTo(Duration.ofHours(rules.getAdvanceHours()))<0)deduction=deduction.max(paid.multiply(remaining.compareTo(Duration.ofHours(rules.getNearHours()))<0?rules.getNearRate():rules.getIntermediateRate()));
  }else throw new IllegalArgumentException("Passenger status is not refundable");
  deduction=deduction.setScale(2,RoundingMode.HALF_UP).min(paid).setScale(2);paid=paid.setScale(2);
  return new Calculation(paid,deduction,paid.subtract(deduction));
 }
 public record Calculation(BigDecimal paid,BigDecimal deduction,BigDecimal refund){}

 /** Claim and finalization use short DB transactions; gateway I/O never holds reservation locks. */
 public void process(String id){
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Refund gateway processing must run outside the cancellation transaction");
  Claim claim=tx.execute(t->claim(id));if(claim==null)return;
  try{
   if(!provider.type().equals(claim.providerType()))throw new IllegalStateException("Payment provider adapter does not match saved payment");
   var result=provider.refund(new PaymentProvider.RefundCommand(claim.paymentId(),claim.amount(),id));
   boolean success=(result.status()==PaymentStatus.REFUNDED||result.status()==PaymentStatus.PARTIALLY_REFUNDED)&&result.refundedAmount().compareTo(claim.amount())==0&&result.providerPaymentId().equals(claim.paymentId())&&result.providerType().equals(claim.providerType());
   tx.executeWithoutResult(t->finish(id,claim.token(),success,success?null:"Provider did not confirm the expected refund"));
  }catch(Exception error){log.error("Refund processing failed for request {}",id,error);tx.executeWithoutResult(t->finish(id,claim.token(),false,"Provider refund could not be verified; retry uses the same reference"));}
 }
 private Claim claim(String id){
  CancellationQuote initial=quotes.findById(id).orElse(null);if(initial==null||initial.getConfirmedAt()==null)return null;
  PaymentTransaction payment=payments.lockByBookingId(initial.getBooking().getId()).orElse(null);
  CancellationQuote quote=quotes.lockById(id).orElseThrow();em.refresh(quote);
  boolean eligible="REFUND_PENDING".equals(quote.getRefundStatus())||("REFUND_PROCESSING".equals(quote.getRefundStatus())&&quote.getProcessingAt()!=null&&quote.getProcessingAt().isBefore(Instant.now().minusSeconds(leaseSeconds)));
  if(!eligible)return null;
  if(payment==null){quote.failed("Saved successful payment is missing");publishUpdate(quote.getBooking());return null;}
  if(!List.of(PaymentStatus.SUCCESS,PaymentStatus.PARTIALLY_REFUNDED).contains(payment.getStatus())||quote.getRefund().add(payment.getRefundedAmount()).compareTo(payment.getAmount())>0){quote.failed("Refund exceeds the remaining verified payment");publishUpdate(quote.getBooking());return null;}
  if(quotes.existsByBookingIdAndRefundStatusAndIdNot(quote.getBooking().getId(),"REFUND_PROCESSING",id))return null;
  String token=UUID.randomUUID().toString();quote.processing(token);
  publishUpdate(quote.getBooking());
  return new Claim(payment.getProviderType(),payment.getProviderPaymentId(),quote.getRefund(),token);
 }
 private void finish(String id,String token,boolean success,String reason){
  CancellationQuote initial=quotes.findById(id).orElseThrow();
  PaymentTransaction payment=payments.lockByBookingId(initial.getBooking().getId()).orElseThrow();
  CancellationQuote quote=quotes.lockById(id).orElseThrow();em.refresh(quote);
  if(!"REFUND_PROCESSING".equals(quote.getRefundStatus())||!token.equals(quote.getProcessingToken()))return;
  if(success){payment.recordRefund(quote.getRefund());quote.refunded();}else quote.failed(reason);
  publishUpdate(quote.getBooking());
 }
 public void retry(String id){tx.executeWithoutResult(t->{
  CancellationQuote initial=quotes.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Refund not found"));
  payments.lockByBookingId(initial.getBooking().getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Saved payment is missing"));
  CancellationQuote quote=quotes.lockById(id).orElseThrow();em.refresh(quote);
  if(!"REFUND_FAILED".equals(quote.getRefundStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed refunds can be retried");quote.retry();publishUpdate(quote.getBooking());
 });}
 public List<RefundView> history(AuthPrincipal user,String reference){return tx.execute(t->{
  Booking booking=bookings.findByInternalBookingReference(reference.trim().toUpperCase(Locale.ROOT)).filter(b->b.getUserId().equals(user.id())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));
  return quotes.findByBookingIdAndConfirmedAtIsNotNullOrderByConfirmedAtDesc(booking.getId()).stream().map(q->new RefundView(q.getId(),q.getCharge().add(q.getRefund()),q.getCharge(),q.getRefund(),q.getRefundStatus(),q.getRefundAttempts(),q.getRefundFailure())).toList();
 });}
 private record Claim(String providerType,String paymentId,BigDecimal amount,String token){}
 public record RefundView(String refundReference,BigDecimal paid,BigDecimal deduction,BigDecimal refund,String status,int attempts,String failure){}
}
