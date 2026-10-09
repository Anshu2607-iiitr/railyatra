package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.*;
import com.railyatra.auth.*;
import com.railyatra.passenger.*;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.repository.*;
import com.railyatra.railway.service.SeatAllocationService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"railyatra.holds.expiry-check-ms=600000","railyatra.refunds.worker-enabled=false"})
class CancellationIntegrationTest {
 @Autowired CancellationService cancellations;
 @Autowired BookingRepository bookings;
 @Autowired BookingPassengerRepository passengers;
 @Autowired CancellationQuoteRepository quotes;
 @Autowired TrainRepository trains;
 @Autowired TrainClassRepository classes;
 @Autowired CoachRepository coaches;
 @Autowired SeatRepository seats;
 @Autowired TrainStopRepository stops;
 @Autowired PaymentTransactionRepository payments;
 @Autowired com.railyatra.payment.RefundService refunds;
 @Autowired com.railyatra.payment.MockRefundSettlementService mockSettlements;
 @Autowired StationRepository stations;
 @Autowired UserRepository users;
 @Autowired SeatHoldRepository holds;
 @Autowired SeatInventoryRepository inventories;
 @Autowired SeatAllocationService allocations;
 @Autowired PlatformTransactionManager manager;
 @Autowired JdbcTemplate jdbc;

 @Test void partialCancellationPromotesRacThenWaitlistAndKeepsOtherPassenger(){
  Fixture f=fixture();
  var quote=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst()));
  assertEquals(new BigDecimal("60.00"),quote.cancellationCharge());
  assertEquals(new BigDecimal("440.00"),quote.estimatedRefund());
  var confirmed=cancellations.confirm(f.user(),f.reference(),quote.quoteId());
  assertTrue(confirmed.confirmed()); assertEquals("REFUND_PENDING",confirmed.refundStatus());
  assertEquals(1,passengers.countByBookingIdAndCancelledFalse(f.bookingId()));
  assertEquals(BookingStatus.CONFIRMED,bookings.findById(f.bookingId()).orElseThrow().getCurrentStatus());
  assertEquals(BookingStatus.CONFIRMED,bookings.findByInternalBookingReference(f.racReference()).orElseThrow().getCurrentStatus());
  assertEquals(BookingStatus.RAC,bookings.findByInternalBookingReference(f.wlReference()).orElseThrow().getCurrentStatus());
  assertEquals(0,inventories.findById(f.inventoryId()).orElseThrow().getAvailableCount());
  assertEquals(Integer.valueOf(2),jdbc.queryForObject("select count(*) from seat_allocations where seat_inventory_id=?",Integer.class,f.inventoryId()));
  var remaining=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getLast()));
  cancellations.confirm(f.user(),f.reference(),remaining.quoteId());
  assertEquals(BookingStatus.CANCELLED,bookings.findById(f.bookingId()).orElseThrow().getCurrentStatus());
  assertEquals(BookingStatus.CONFIRMED,bookings.findByInternalBookingReference(f.wlReference()).orElseThrow().getCurrentStatus());
 }

 @Test void concurrentConfirmationOfSameQuoteIsIdempotent() throws Exception {
  Fixture f=fixture(); var quote=cancellations.quote(f.user(),f.reference(),f.passengerIds());
  ExecutorService executor=Executors.newFixedThreadPool(2); CyclicBarrier barrier=new CyclicBarrier(2);
  try {
   Callable<CancellationService.QuoteResponse> work=()->{barrier.await(10,TimeUnit.SECONDS);return cancellations.confirm(f.user(),f.reference(),quote.quoteId());};
   Future<CancellationService.QuoteResponse> first=executor.submit(work),second=executor.submit(work);
   assertTrue(first.get(20,TimeUnit.SECONDS).confirmed()); assertTrue(second.get(20,TimeUnit.SECONDS).confirmed());
  } finally {executor.shutdownNow();}
  assertEquals(Integer.valueOf(1),jdbc.queryForObject("select count(*) from cancellation_quotes where booking_id=? and confirmed_at is not null",Integer.class,f.bookingId()));
  assertEquals(0,passengers.countByBookingIdAndCancelledFalse(f.bookingId()));
  assertEquals(Integer.valueOf(2),jdbc.queryForObject("select count(*) from seat_allocations where seat_inventory_id=?",Integer.class,f.inventoryId()));
 }

 @Test void failureRollsBackPassengerInventoryPromotionAndRefundTogether(){
  Fixture f=fixture(); var quote=cancellations.quote(f.user(),f.reference(),f.passengerIds());
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(tx->{cancellations.confirm(f.user(),f.reference(),quote.quoteId());throw new IllegalStateException("Force rollback");}));
  assertEquals(2,passengers.countByBookingIdAndCancelledFalse(f.bookingId()));
  assertNull(quotes.findById(quote.quoteId()).orElseThrow().getConfirmedAt());
  assertEquals(BookingStatus.RAC,bookings.findByInternalBookingReference(f.racReference()).orElseThrow().getCurrentStatus());
  assertEquals(BookingStatus.WAITLISTED,bookings.findByInternalBookingReference(f.wlReference()).orElseThrow().getCurrentStatus());
  assertEquals(0,inventories.findById(f.inventoryId()).orElseThrow().getAvailableCount());
 }

 @Test void ownershipAndPassengerSelectionAreValidated(){
  Fixture f=fixture();
  var stranger=new AuthPrincipal(f.user().id()+100000,"other@test.local","ROLE_USER");
  assertEquals(404,assertThrows(ResponseStatusException.class,()->cancellations.quote(stranger,f.reference(),f.passengerIds())).getStatusCode().value());
  assertThrows(ResponseStatusException.class,()->cancellations.quote(f.user(),f.reference(),List.of(Long.MAX_VALUE)));
  assertThrows(ResponseStatusException.class,()->cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst(),f.passengerIds().getFirst())));
 }

 @Test void expiredQuotesCannotCancelPassengers(){
  Fixture f=fixture();var quote=cancellations.quote(f.user(),f.reference(),f.passengerIds());
  jdbc.update("update cancellation_quotes set expires_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),quote.quoteId());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->cancellations.confirm(f.user(),f.reference(),quote.quoteId())).getStatusCode().value());
  assertEquals(2,passengers.countByBookingIdAndCancelledFalse(f.bookingId()));
 }

 @Test void promotionInvalidatesAQuoteWhenTheChargeChanges(){
  Fixture f=fixture();
  Long wlPassenger=new TransactionTemplate(manager).execute(tx->passengers.findByBookingIdOrderByPassengerIndexAsc(bookings.findByInternalBookingReference(f.wlReference()).orElseThrow().getId()).getFirst().getId());
  var oldQuote=cancellations.quote(f.user(),f.wlReference(),List.of(wlPassenger));
  var release=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst()));
  cancellations.confirm(f.user(),f.reference(),release.quoteId());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->cancellations.confirm(f.user(),f.wlReference(),oldQuote.quoteId())).getStatusCode().value());
  assertNull(quotes.findById(oldQuote.quoteId()).orElseThrow().getConfirmedAt());
 }

 @Test void partialRefundsSettleExactlyOnceAcrossConcurrentWorkers() throws Exception {
  Fixture f=fixture();var quote=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst()));
  cancellations.confirm(f.user(),f.reference(),quote.quoteId());
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try {Future<?> a=pool.submit(()->refunds.process(quote.quoteId())),b=pool.submit(()->refunds.process(quote.quoteId()));a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  refunds.process(quote.quoteId());
  assertEquals("REFUNDED",quotes.findById(quote.quoteId()).orElseThrow().getRefundStatus());
  assertEquals(Integer.valueOf(1),jdbc.queryForObject("select count(*) from mock_refund_receipts where idempotency_key=?",Integer.class,quote.quoteId()));
  var next=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getLast()));
  cancellations.confirm(f.user(),f.reference(),next.quoteId());refunds.process(next.quoteId());
  assertEquals(new BigDecimal("880.00"),jdbc.queryForObject("select refunded_amount from payment_transactions where booking_id=?",BigDecimal.class,f.bookingId()));
 }

 @Test void persistedProviderReceiptRecoversARefundAfterAnInterruptedCompletion(){
  Fixture f=fixture();var quote=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst()));cancellations.confirm(f.user(),f.reference(),quote.quoteId());
  String paymentId=bookings.findById(f.bookingId()).orElseThrow().getPaymentReference();
  var restartedProvider=new com.railyatra.payment.MockPaymentProvider(mockSettlements);
  restartedProvider.refund(new com.railyatra.payment.PaymentProvider.RefundCommand(paymentId,quote.estimatedRefund(),quote.quoteId()));
  refunds.process(quote.quoteId());
  assertEquals("REFUNDED",quotes.findById(quote.quoteId()).orElseThrow().getRefundStatus());
  assertEquals(Integer.valueOf(1),jdbc.queryForObject("select count(*) from mock_refund_receipts where idempotency_key=?",Integer.class,quote.quoteId()));
  assertEquals(new BigDecimal("440.00"),jdbc.queryForObject("select refunded_amount from payment_transactions where booking_id=?",BigDecimal.class,f.bookingId()));
 }

 @Test void failedRefundCanRetryWithTheSameReference(){
  Fixture f=fixture();var quote=cancellations.quote(f.user(),f.reference(),List.of(f.passengerIds().getFirst()));cancellations.confirm(f.user(),f.reference(),quote.quoteId());
  jdbc.update("update payment_transactions set provider_type='OTHER' where booking_id=?",f.bookingId());refunds.process(quote.quoteId());
  assertEquals("REFUND_FAILED",quotes.findById(quote.quoteId()).orElseThrow().getRefundStatus());
  jdbc.update("update payment_transactions set provider_type='MOCK' where booking_id=?",f.bookingId());refunds.retry(quote.quoteId());refunds.process(quote.quoteId());
  assertEquals("REFUNDED",quotes.findById(quote.quoteId()).orElseThrow().getRefundStatus());
  assertEquals(2,quotes.findById(quote.quoteId()).orElseThrow().getRefundAttempts());
 }

 private Fixture fixture(){return new TransactionTemplate(manager).execute(tx->{
  String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
  User user=users.save(new User("Cancellation Test","cancel"+suffix+"@test.local","9"+Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L),"hash"));
  Train train=trains.save(new Train("C"+suffix,"Cancellation Train"));
  TrainClass cls=classes.save(new TrainClass(train,"3A","AC 3 Tier"));
  Coach coach=coaches.save(new Coach(cls,"B1",1)); seats.save(new Seat(coach,"1",SeatType.LOWER));seats.save(new Seat(coach,"2",SeatType.UPPER));
  Station origin=stations.save(new Station("O"+suffix,"Origin","City","State")), destination=stations.save(new Station("D"+suffix,"Destination","City","State"));
  stops.save(new TrainStop(train,origin,1,null,LocalTime.NOON,0,0));
  stops.save(new TrainStop(train,destination,2,LocalTime.of(14,0),null,0,100));
  LocalDate date=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5);
  SeatInventory inventory=inventories.saveAndFlush(new SeatInventory(train,cls,date,"GENERAL",2,1));
  inventory.reserveConfirmedSeats(2);
  Booking confirmed=booking(user,inventory,origin,destination,2,HoldReservationType.CONFIRMED);
  var requests=List.of(request("One"),request("Two"));
  var assigned=allocations.allocateFromHold(confirmed.getHold(),confirmed.getInternalBookingReference(),requests);
  passengers.save(new BookingPassenger(confirmed,0,requests.get(0),assigned.assignments().get(0)));
  passengers.save(new BookingPassenger(confirmed,1,requests.get(1),assigned.assignments().get(1)));
  inventory.reserveRacSeats(1);Booking rac=booking(user,inventory,origin,destination,1,HoldReservationType.RAC);
  passengers.save(new BookingPassenger(rac,0,request("Rac"),null));
  inventory.addWaitingList(1);Booking wl=booking(user,inventory,origin,destination,1,HoldReservationType.WAITLISTED);
  passengers.save(new BookingPassenger(wl,0,request("Waitlisted"),null));passengers.flush();
  return new Fixture(new AuthPrincipal(user.getId(),user.getEmail(),"ROLE_USER"),confirmed.getId(),confirmed.getInternalBookingReference(),inventory.getId(),passengers.findByBookingIdOrderByPassengerIndexAsc(confirmed.getId()).stream().map(BookingPassenger::getId).toList(),rac.getInternalBookingReference(),wl.getInternalBookingReference());
 });}
 private Booking booking(User user,SeatInventory inventory,Station origin,Station destination,int count,HoldReservationType type){
  SeatHold hold=holds.save(new SeatHold(user.getId(),inventory.getTrain(),inventory.getTrainClass(),inventory.getJourneyDate(),inventory.getQuota(),count,inventory,UUID.randomUUID().toString(),Instant.now().plusSeconds(600),type));hold.consume();
  Booking booking=new Booking(user.getId(),hold,origin,destination,"RY"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT),UUID.randomUUID().toString(),new BigDecimal("1000.00"));
  booking.markPaid("PAY"+UUID.randomUUID(),type==HoldReservationType.CONFIRMED?BookingStatus.CONFIRMED:type==HoldReservationType.RAC?BookingStatus.RAC:BookingStatus.WAITLISTED,type==HoldReservationType.CONFIRMED?null:1);
  bookings.saveAndFlush(booking);
  payments.save(new PaymentTransaction(booking,booking.getPaymentReference(),booking.getTotalFare(),com.railyatra.payment.PaymentStatus.SUCCESS,"MOCK",com.railyatra.payment.PaymentMethod.UPI));
  return booking;
 }
 private PassengerDetailsRequest request(String name){return new PassengerDetailsRequest("Passenger "+name,25,Gender.FEMALE,BerthPreference.NO_PREFERENCE);}
 private record Fixture(AuthPrincipal user,String bookingId,String reference,Long inventoryId,List<Long> passengerIds,String racReference,String wlReference){}
}
