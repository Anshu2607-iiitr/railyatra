package com.railyatra.booking;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Committed DB events reach authenticated subscribers on every backend instance. */
@Component
public class BookingStatusEventPublisher {
 private final JdbcTemplate jdbc;
 private final org.springframework.transaction.support.TransactionTemplate tx;
 private final Map<Long,Set<Subscription>> subscribers=new ConcurrentHashMap<>();
 public BookingStatusEventPublisher(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new org.springframework.transaction.support.TransactionTemplate(manager);}

 public SseEmitter subscribe(Long userId,String reference){
  long cursor=jdbc.queryForObject("select coalesce(max(delivery_id),0) from booking_status_events where user_id=?",Long.class,userId);
  Subscription sub=new Subscription(userId,reference,new SseEmitter(60000L),cursor);
  subscribers.compute(userId,(id,existing)->{Set<Subscription> streams=existing==null?ConcurrentHashMap.newKeySet():existing;if(streams.size()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many live booking connections");streams.add(sub);return streams;});
  Runnable remove=()->remove(sub);
  sub.emitter.onCompletion(remove);sub.emitter.onTimeout(()->{remove.run();sub.emitter.complete();});sub.emitter.onError(error->remove.run());
  try{sub.emitter.send(SseEmitter.event().name("connected").data(Map.of("refreshRequired",true)));}
  catch(IOException|IllegalStateException error){remove.run();sub.emitter.complete();}
  return sub.emitter;
 }
 /** Insert in the business transaction: rollback removes the event, polling only reads committed rows. */
 public void publishAfterCommit(Booking booking){record(booking,"booking-status-change");}
 public void publishRefundAfterCommit(Booking booking){record(booking,"refund-status-change");}
 private void record(Booking booking,String type){jdbc.update("insert into booking_status_events(user_id,internal_reference,event_type,created_at) values(?,?,?,?)",booking.getUserId(),booking.getInternalBookingReference(),type,java.sql.Timestamp.from(Instant.now()));}

 @Scheduled(fixedDelayString="${railyatra.status-events.poll-ms:1000}")
 public void dispatch(){
  relayCommittedEvents();
  for(Set<Subscription> streams:subscribers.values())for(Subscription sub:streams){
   List<Event> events=sub.reference==null
    ?jdbc.query("select delivery_id,internal_reference,event_type from booking_status_events where user_id=? and delivery_id>? order by delivery_id limit 100",(rs,i)->new Event(rs.getLong(1),rs.getString(2),rs.getString(3)),sub.userId,sub.cursor)
    :jdbc.query("select delivery_id,internal_reference,event_type from booking_status_events where user_id=? and internal_reference=? and delivery_id>? order by delivery_id limit 100",(rs,i)->new Event(rs.getLong(1),rs.getString(2),rs.getString(3)),sub.userId,sub.reference,sub.cursor);
   try{
    for(Event event:events){sub.emitter.send(SseEmitter.event().id(Long.toString(event.id())).name(event.type()).data(Map.of("internalReference",event.reference(),"refreshRequired",true)));sub.cursor=event.id();}
    if(System.currentTimeMillis()-sub.lastHeartbeat>=10000){sub.emitter.send(SseEmitter.event().comment("heartbeat"));sub.lastHeartbeat=System.currentTimeMillis();}
   }catch(IOException|IllegalStateException error){remove(sub);sub.emitter.complete();}
  }
 }
 private void remove(Subscription sub){subscribers.computeIfPresent(sub.userId,(id,streams)->{streams.remove(sub);return streams.isEmpty()?null:streams;});}
 /** Delivery sequence is assigned AFTER business commit. Late commits cannot be skipped by a cursor. */
 public void relayCommittedEvents(){tx.executeWithoutResult(t->{
  long sequence=jdbc.queryForObject("select last_sequence from booking_event_delivery_clock where id=1 for update",Long.class);
  List<Long> ids=jdbc.queryForList("select id from booking_status_events where delivery_id is null order by id limit 500",Long.class);
  for(Long id:ids)jdbc.update("update booking_status_events set delivery_id=? where id=?",++sequence,id);
  if(!ids.isEmpty())jdbc.update("update booking_event_delivery_clock set last_sequence=? where id=1",sequence);
 });}
 private record Event(long id,String reference,String type){}
 private static final class Subscription {
  final Long userId;final String reference;final SseEmitter emitter;volatile long cursor;volatile long lastHeartbeat=System.currentTimeMillis();
  Subscription(Long userId,String reference,SseEmitter emitter,long cursor){this.userId=userId;this.reference=reference;this.emitter=emitter;this.cursor=cursor;}
 }
}
