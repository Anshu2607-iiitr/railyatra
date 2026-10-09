package com.railyatra.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.railyatra.auth.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"railyatra.status-events.poll-ms=600000","railyatra.refunds.worker-enabled=false"})
class BookingRealtimeTest {
 @Autowired BookingStatusEventPublisher events;
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager manager;
 @Autowired UserRepository users;
 private Booking eventBooking(Long userId,String reference){Booking b=mock(Booking.class);when(b.getUserId()).thenReturn(userId);when(b.getInternalBookingReference()).thenReturn(reference);return b;}
 private User user(){String id=UUID.randomUUID().toString();return users.save(new User("Live Test","live"+id+"@example.test","9"+Math.abs(UUID.randomUUID().getMostSignificantBits()%1000000000L),"hash"));}

 @Test void rolledBackBookingChangesNeverProduceDeliverableEvents(){
  User user=user();Booking booking=eventBooking(user.getId(),"RYROLLBACK");
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(tx->{events.publishAfterCommit(booking);throw new IllegalStateException("Rollback");}));
  events.relayCommittedEvents();
  assertEquals(Integer.valueOf(0),jdbc.queryForObject("select count(*) from booking_status_events where user_id=?",Integer.class,user.getId()));
 }

 @Test void lateCommitReceivesNewDeliveryIdAndCannotBeMissed() throws Exception {
  User user=user();CountDownLatch inserted=new CountDownLatch(1),release=new CountDownLatch(1);
  ExecutorService executor=Executors.newSingleThreadExecutor();
  Future<?> early=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(tx->{events.publishAfterCommit(eventBooking(user.getId(),"RYEARLY"));inserted.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timed out");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}));
  try{
   assertTrue(inserted.await(10,TimeUnit.SECONDS));
   new TransactionTemplate(manager).executeWithoutResult(tx->events.publishRefundAfterCommit(eventBooking(user.getId(),"RYLATER")));
   // A second backend instance can relay and see the same committed outbox.
   BookingStatusEventPublisher otherInstance=new BookingStatusEventPublisher(jdbc,manager);otherInstance.relayCommittedEvents();
   Long laterDelivery=jdbc.queryForObject("select delivery_id from booking_status_events where user_id=? and internal_reference='RYLATER'",Long.class,user.getId());
   release.countDown();early.get(10,TimeUnit.SECONDS);otherInstance.relayCommittedEvents();
   Long earlyDelivery=jdbc.queryForObject("select delivery_id from booking_status_events where user_id=? and internal_reference='RYEARLY'",Long.class,user.getId());
   assertTrue(earlyDelivery>laterDelivery);
  }finally{release.countDown();executor.shutdownNow();}
 }

 @Test void ownerCheckRejectsOtherUsersAndUnauthenticatedSubscribers(){
  BookingRepository repository=mock(BookingRepository.class);BookingPassengerRepository passengers=mock(BookingPassengerRepository.class);
  when(repository.findByInternalBookingReference("RYPRIVATE")).thenReturn(java.util.Optional.of(eventBooking(7L,"RYPRIVATE")));
  PnrService pnr=new PnrService(repository,passengers);
  pnr.requireOwner(new AuthPrincipal(7L,"owner@test.local","ROLE_USER"),"ryprivate");
  assertEquals(404,assertThrows(ResponseStatusException.class,()->pnr.requireOwner(new AuthPrincipal(8L,"other@test.local","ROLE_ADMIN"),"RYPRIVATE")).getStatusCode().value());
  assertEquals(401,assertThrows(ResponseStatusException.class,()->pnr.requireOwner(null,"RYPRIVATE")).getStatusCode().value());
 }
}
