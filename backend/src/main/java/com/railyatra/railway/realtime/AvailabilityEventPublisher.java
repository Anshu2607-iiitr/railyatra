package com.railyatra.railway.realtime;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AvailabilityEventPublisher {
 private final JdbcTemplate jdbc;
 private final Map<Key,Set<SseEmitter>> subscribers=new ConcurrentHashMap<>();
 private final Map<Key,String> versions=new ConcurrentHashMap<>();
 public AvailabilityEventPublisher(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public SseEmitter subscribe(String train,LocalDate date,String cls,String quota){
  Key key=key(train,date,cls,quota);SseEmitter emitter=new SseEmitter(60000L);
  // Read before registering; a concurrent change is caught by the next poll or connected refetch.
  String version=signature(key);versions.putIfAbsent(key,version);
  subscribers.computeIfAbsent(key,k->ConcurrentHashMap.newKeySet()).add(emitter);
  Runnable remove=()->subscribers.computeIfPresent(key,(k,set)->{set.remove(emitter);if(set.isEmpty()){versions.remove(k);return null;}return set;});
  emitter.onCompletion(remove);emitter.onTimeout(()->{remove.run();emitter.complete();});emitter.onError(e->remove.run());
  try{emitter.send(SseEmitter.event().name("connected").data("refresh"));}
  catch(IOException|IllegalStateException ex){remove.run();emitter.complete();}
  return emitter;
 }
 public void publish(String train,LocalDate date,String cls,String quota,String reason){
  Key key=key(train,date,cls,quota);
  AvailabilityEvent event=new AvailabilityEvent(key.train(),date,key.cls(),key.quota(),reason,java.time.Instant.now());
  for(SseEmitter emitter:subscribers.getOrDefault(key,Set.of()))try{emitter.send(SseEmitter.event().name("availability-change").data(event));}
  catch(IOException|IllegalStateException ex){emitter.complete();}
 }
 public void publishAfterCommit(String train,LocalDate date,String cls,String quota,String reason){
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){publish(train,date,cls,quota,reason);}
  });else publish(train,date,cls,quota,reason);
 }
 /** Compare committed DB revisions on each instance: changes made by another server are visible too. */
 @Scheduled(fixedDelayString="${railyatra.availability.poll-ms:3000}")
 public void refresh(){
  for(Key key:subscribers.keySet()){
   try{
    String current=signature(key),previous=versions.put(key,current);
    if(!Objects.equals(previous,current))publish(key.train(),key.date(),key.cls(),key.quota(),"INVENTORY_REFRESH_REQUIRED");
    else for(SseEmitter emitter:subscribers.getOrDefault(key,Set.of()))try{emitter.send(SseEmitter.event().comment("heartbeat"));}catch(IOException|IllegalStateException ex){emitter.complete();}
   }catch(RuntimeException ex){
    org.slf4j.LoggerFactory.getLogger(getClass()).error("Availability refresh failed",ex);
    for(SseEmitter emitter:subscribers.getOrDefault(key,Set.of()))emitter.completeWithError(ex);
   }
  }
 }
 private String signature(Key key){
  var rows=jdbc.query("select i.version,i.available_count,i.rac_available_count,i.waiting_list_count from seat_inventories i join trains t on t.id=i.train_id join train_classes c on c.id=i.train_class_id where t.train_number=? and i.journey_date=? and c.class_code=? and i.quota=?",
   (rs,n)->rs.getLong(1)+":"+rs.getInt(2)+":"+rs.getInt(3)+":"+rs.getInt(4),key.train(),key.date(),key.cls(),key.quota());
  return rows.isEmpty()?"uninitialized":rows.getFirst();
 }
 private Key key(String train,LocalDate date,String cls,String quota){return new Key(train.trim().toUpperCase(Locale.ROOT),date,cls.trim().toUpperCase(Locale.ROOT),quota.trim().toUpperCase(Locale.ROOT).replace(' ','_'));}
 private record Key(String train,LocalDate date,String cls,String quota){}
}
