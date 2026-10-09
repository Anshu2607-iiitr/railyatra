package com.railyatra.booking;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.railway.repository.SeatInventoryRepository;
import com.railyatra.railway.repository.TrainStopRepository;
import com.railyatra.payment.RefundService;
import com.railyatra.payment.RefundRules;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CancellationService {
 private final BookingRepository bookings; private final BookingPassengerRepository passengers;
 private final CancellationQuoteRepository quotes; private final CancellationPolicy policy;
 private final SeatInventoryRepository inventories; private final ReservationStatusService status; private final EntityManager em;
 private final RefundService refunds; private final RefundRules rules; private final TrainStopRepository stops;
 public CancellationService(BookingRepository bookings,BookingPassengerRepository passengers,CancellationQuoteRepository quotes,CancellationPolicy policy,SeatInventoryRepository inventories,ReservationStatusService status,EntityManager em,RefundService refunds,RefundRules rules,TrainStopRepository stops){this.bookings=bookings;this.passengers=passengers;this.quotes=quotes;this.policy=policy;this.inventories=inventories;this.status=status;this.em=em;this.refunds=refunds;this.rules=rules;this.stops=stops;}

 @Transactional
 public QuoteResponse quote(AuthPrincipal user,String reference,List<Long> ids){
  Booking booking=owned(user,reference); eligible(booking);
  List<BookingPassenger> all=passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId());
  List<BookingPassenger> selected=select(all,ids);
  Amounts amounts=amounts(booking,all,selected);
  String selection=selected.stream().map(p->p.getId().toString()).collect(Collectors.joining(","));
  CancellationQuote quote=quotes.save(new CancellationQuote(booking,selection,amounts.charge(),amounts.refund(),Instant.now().plusSeconds(300)));
  return response(quote,selected);
 }

 @Transactional(readOnly=true)
 public List<QuoteResponse> history(AuthPrincipal user,String reference){
  Booking booking=owned(user,reference);
  List<BookingPassenger> all=passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId());
  return quotes.findByBookingIdAndConfirmedAtIsNotNullOrderByConfirmedAtDesc(booking.getId()).stream().map(q->{
   Set<Long> ids=Arrays.stream(q.getPassengerIds().split(",")).map(Long::valueOf).collect(Collectors.toSet());
   return response(q,all.stream().filter(p->ids.contains(p.getId())).toList());
  }).toList();
 }

 /** Inventory lock serializes cancellation, allocation and queue promotion across instances. */
 @Transactional
 public QuoteResponse confirm(AuthPrincipal user,String reference,String quoteId){
  Booking booking=owned(user,reference);
  inventories.findByIdForUpdate(booking.getHold().getInventory().getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Inventory missing"));
  em.refresh(booking); // The booking may have changed while waiting for the inventory lock.
  CancellationQuote quote=quotes.findById(quoteId).filter(q->q.getBooking().getId().equals(booking.getId())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cancellation quote not found"));
  List<BookingPassenger> all=passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId());
  List<Long> ids=Arrays.stream(quote.getPassengerIds().split(",")).map(Long::valueOf).toList();
  if(quote.getConfirmedAt()!=null) return response(quote,all.stream().filter(p->ids.contains(p.getId())).toList());
  if(!quote.getExpiresAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Quote expired. Request a fresh cancellation review");
  eligible(booking);
  List<BookingPassenger> selected=select(all,ids); Amounts amounts=amounts(booking,all,selected);
  if(amounts.charge().compareTo(quote.getCharge())!=0||amounts.refund().compareTo(quote.getRefund())!=0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation status changed. Review a fresh cancellation quote");
  status.cancelPassengers(booking,selected);
  quote.confirm(); // Durable refund request committed atomically with cancellation, no external payment side effect.
  refunds.publishUpdate(booking);
  return response(quote,selected);
 }
 private Booking owned(AuthPrincipal user,String reference){
  if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
  return bookings.findByInternalBookingReference(reference.trim().toUpperCase(Locale.ROOT)).filter(b->b.getUserId().equals(user.id())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));
 }
 private void eligible(Booking booking){
  if(!"LOCAL".equals(booking.getProviderType()))throw new ResponseStatusException(HttpStatus.CONFLICT,"External-provider cancellation requires the authorized provider adapter");
  if(!MyBookingsService.canCancel(booking,LocalDate.now(ZoneId.of("Asia/Kolkata"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation is no longer eligible for cancellation");
 }
 private List<BookingPassenger> select(List<BookingPassenger> all,List<Long> ids){
  if(ids==null||ids.isEmpty()||ids.size()>6||new HashSet<>(ids).size()!=ids.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select 1 to 6 distinct passengers");
  List<BookingPassenger> selected=all.stream().filter(p->ids.contains(p.getId())&&!p.isCancelled()).toList();
  if(selected.size()!=ids.size())throw new ResponseStatusException(HttpStatus.CONFLICT,"Selected passengers are invalid or already cancelled");
  return selected;
 }
 private Amounts amounts(Booking booking,List<BookingPassenger> all,List<BookingPassenger> selected){
  BigDecimal charge=BigDecimal.ZERO,refund=BigDecimal.ZERO;
  var departure=stops.findByTrainIdAndStationStationCode(booking.getTrain().getId(),booking.getOriginStation().getStationCode()).map(s->s.getDepartureTime()).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Boarding departure time is missing"));
  Instant journeyTime=booking.getJourneyDate().atTime(departure).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),cancelTime=Instant.now();
  for(BookingPassenger passenger:selected){BigDecimal paid=policy.passengerFare(booking.getTotalFare(),all.size(),passenger.getPassengerIndex());var calculation=refunds.calculate(paid,journeyTime,cancelTime,booking.getTrainClass().getClassCode(),booking.getCurrentStatus(),rules);charge=charge.add(calculation.deduction());refund=refund.add(calculation.refund());}
  return new Amounts(charge,refund);
 }
 private QuoteResponse response(CancellationQuote quote,List<BookingPassenger> selected){return new QuoteResponse(quote.getId(),quote.getBooking().getInternalBookingReference(),selected.stream().map(p->new Passenger(p.getId(),p.getPassengerName())).toList(),quote.getCharge(),quote.getRefund(),quote.getExpiresAt(),quote.getConfirmedAt()!=null,quote.getRefundStatus(),"RailYatra development refund rules. Settlement is simulated; no real funds are transferred.");}
 private record Amounts(BigDecimal charge,BigDecimal refund){}
 public record Passenger(Long id,String name){}
 public record QuoteResponse(String quoteId,String reference,List<Passenger> selectedPassengers,BigDecimal cancellationCharge,BigDecimal estimatedRefund,Instant expiresAt,boolean confirmed,String refundStatus,String policyNotice){}
}
