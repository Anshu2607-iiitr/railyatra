package com.railyatra.booking;

import com.railyatra.auth.*;
import com.railyatra.passenger.*;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.realtime.AvailabilityEventPublisher;
import com.railyatra.railway.service.*;
import com.railyatra.payment.PaymentProvider;
import java.math.RoundingMode;
import java.util.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
 private final BookingRepository bookings; private final BookingPassengerRepository passengers; private final PaymentTransactionRepository payments;
 private final UserRepository users; private final PassengerService passengerService; private final Validator validator;
 private final SeatHoldService holds; private final SeatAllocationService allocations; private final FareService fares; private final FareCalculatorService fareCalculator; private final AvailabilityEventPublisher events; private final InternalReferenceGenerator references; private final ReservationStatusService statusEngine; private final PaymentProvider paymentProvider;
 public BookingService(BookingRepository bookings, BookingPassengerRepository passengers, PaymentTransactionRepository payments, UserRepository users, PassengerService passengerService, Validator validator, SeatHoldService holds, SeatAllocationService allocations, FareService fares, FareCalculatorService fareCalculator, AvailabilityEventPublisher events, InternalReferenceGenerator references, ReservationStatusService statusEngine, PaymentProvider paymentProvider) { this.bookings=bookings;this.passengers=passengers;this.payments=payments;this.users=users;this.passengerService=passengerService;this.validator=validator;this.holds=holds;this.allocations=allocations;this.fares=fares;this.fareCalculator=fareCalculator;this.events=events;this.references=references;this.statusEngine=statusEngine;this.paymentProvider=paymentProvider; }

 /**
  * The row locks taken by SeatHoldService and SeatAllocationService survive until this transaction commits.
  * Capacity is reserved by the hold; confirmation assigns physical seats without subtracting capacity twice.
  */
 @Transactional
 public BookingResponse create(AuthPrincipal principal, String idempotencyKey, CreateBookingRequest request) {
  validateKey(idempotencyKey);
  Booking previous=bookings.findByUserIdAndIdempotencyKey(principal.id(),idempotencyKey).orElse(null);
  if(previous!=null) return BookingResponse.from(previous,List.of());
  User user=users.findById(principal.id()).filter(User::isActive).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"An active user account is required"));
  SeatHold hold=holds.lockActiveHoldForBooking(user.getId(),request.holdId());
  // A retry can wait behind the first request's hold lock. Recheck after waiting.
  previous=bookings.findByUserIdAndIdempotencyKey(user.getId(),idempotencyKey).orElse(null);
  if(previous!=null) return BookingResponse.from(previous,List.of());
  if(hold.getStatus()!=SeatHoldStatus.ACTIVE) {
   return bookings.findByHoldId(hold.getId()).map(b->BookingResponse.from(b,List.of()))
    .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Seat hold is no longer active"));
  }
  if(request.passengers().size()!=hold.getPassengerCount()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Passenger count must match the active seat hold");
  List<PassengerDetailsRequest> normalized=validatePassengers(request.passengers());
  FareService.FareQuote quote=fares.quote(hold.getTrain(),hold.getTrainClass(),request.originStationCode(),request.destinationStationCode());
  String reference=references.next();
  java.math.BigDecimal authoritativeFare=fareCalculator.calculate(hold.getTrain(),hold.getTrainClass(),request.originStationCode(),request.destinationStationCode(),normalized.size()).total();
  Booking booking=new Booking(user.getId(),hold,quote.origin(),quote.destination(),reference,idempotencyKey.trim(),authoritativeFare);
  PaymentProvider.PaymentVerification verification = paymentProvider.verifyPayment(request.paymentReference().trim(), authoritativeFare);
  if(verification.status() != com.railyatra.payment.PaymentStatus.SUCCESS || verification.verifiedAmount().compareTo(authoritativeFare) != 0) {
   booking.markFailed(request.paymentReference().trim()); bookings.save(booking);
   payments.save(new PaymentTransaction(booking,request.paymentReference().trim(),authoritativeFare,com.railyatra.payment.PaymentStatus.FAILED,verification.providerType(),verification.method()));
   holds.releaseLockedHold(hold,"PAYMENT_FAILED");
   return BookingResponse.from(booking,List.of());
  }
  // In development this is the successful simulated payment boundary. A future provider replaces this adapter call.
  SeatAllocationResult seatResult = hold.getReservationType() == HoldReservationType.CONFIRMED ? allocations.allocateFromHold(hold,reference,normalized) : new SeatAllocationResult(reference, List.of());
  statusEngine.markPaidAndQueue(booking, request.paymentReference().trim()); bookings.save(booking);
  Map<Integer,SeatAllocationResult.AssignedSeat> assigned=new HashMap<>();
  for(SeatAllocationResult.AssignedSeat seat:seatResult.assignments()) assigned.put(seat.passengerIndex(),seat);
  List<BookingPassenger> stored=new ArrayList<>();
  for(int index=0;index<normalized.size();index++) stored.add(new BookingPassenger(booking,index,normalized.get(index),assigned.get(index)));
  passengers.saveAll(stored);
  payments.save(new PaymentTransaction(booking,request.paymentReference().trim(),authoritativeFare,com.railyatra.payment.PaymentStatus.SUCCESS,verification.providerType(),verification.method()));
  holds.consumeLockedHold(hold);
  events.publishAfterCommit(hold.getTrain().getTrainNumber(),hold.getJourneyDate(),hold.getTrainClass().getClassCode(),hold.getQuota(),"BOOKING_" + booking.getCurrentStatus());
  return BookingResponse.from(booking,seatResult.assignments());
 }
 private List<PassengerDetailsRequest> validatePassengers(List<PassengerDetailsRequest> supplied) {
  for(PassengerDetailsRequest passenger:supplied) if(!validator.validate(passenger).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Passenger details are invalid");
  return passengerService.validate(new PassengerBatchRequest(supplied));
 }
 @Transactional(readOnly = true)
 public BookingDetailsResponse getForUser(AuthPrincipal principal, String reference) {
  Booking booking=bookings.findByInternalBookingReference(reference.trim().toUpperCase()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));
  if(!booking.getUserId().equals(principal.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");
  return details(booking);
 }
 private BookingDetailsResponse details(Booking booking) { return new BookingDetailsResponse(booking.getInternalBookingReference(),booking.getProviderType(),booking.getBookingStatus(),booking.getBookingStatusPosition(),booking.getCurrentStatus(),booking.getCurrentStatusPosition(),booking.getTrain().getTrainNumber(),booking.getTrain().getTrainName(),booking.getOriginStation().getStationCode(),booking.getDestinationStation().getStationCode(),booking.getJourneyDate(),booking.getTrainClass().getClassCode(),booking.getQuota(),booking.getTotalFare(),booking.getPaymentReference(),booking.getCreatedAt(),passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId()).stream().map(p->new BookingDetailsResponse.Passenger(p.getId(),p.getPassengerName(),p.getCoachCode(),p.getSeatNumber(),p.getBerthType(),booking.getBookingStatus(),booking.getBookingStatusPosition(),p.isCancelled()?BookingStatus.CANCELLED:booking.getCurrentStatus(),p.isCancelled()?null:booking.getCurrentStatusPosition(),p.isCancelled())).toList()); }
 private void validateKey(String key) { if(key==null||key.isBlank()||key.length()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid Idempotency-Key is required"); }
}
