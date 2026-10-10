package com.railyatra.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railyatra.auth.*;
import com.railyatra.booking.ReservationStatusService;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.repository.SeatInventoryRepository;
import com.railyatra.railway.service.AvailabilityService;
import com.railyatra.railway.realtime.AvailabilityEventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminInventoryService {
 private final JdbcTemplate jdbc; private final SeatInventoryRepository inventory;
 private final AvailabilityService availability; private final ReservationStatusService status;
 private final AvailabilityEventPublisher events; private final UserRepository users;
 private final EntityManager em; private final Validator validator; private final ObjectMapper json;
 public AdminInventoryService(JdbcTemplate jdbc,SeatInventoryRepository inventory,AvailabilityService availability,
  ReservationStatusService status,AvailabilityEventPublisher events,UserRepository users,EntityManager em,Validator validator,ObjectMapper json){
  this.jdbc=jdbc;this.inventory=inventory;this.availability=availability;this.status=status;this.events=events;this.users=users;this.em=em;this.validator=validator;this.json=json;
 }
 private void admin(AuthPrincipal actor){
  if(actor==null)throw error(HttpStatus.UNAUTHORIZED,"Sign in required");
  if(!users.findById(actor.id()).filter(u->u.isActive()&&"ROLE_ADMIN".equals(u.getRole())).isPresent())
   throw error(HttpStatus.FORBIDDEN,"Administrator access is required");
 }
 @Transactional
 public AdminInventoryResponse inspect(AuthPrincipal actor,String train,LocalDate date,String cls,String quota){
  admin(actor);
  if(train==null||!train.trim().matches("[A-Za-z0-9]{1,10}")||date==null||cls==null)throw error(HttpStatus.BAD_REQUEST,"Invalid inventory selection");
  String number=train.trim().toUpperCase(Locale.ROOT),code=cls.trim().toUpperCase(Locale.ROOT),q=quota==null?"GENERAL":quota.trim().toUpperCase(Locale.ROOT).replace(' ','_');
  if(!List.of("1A","2A","3A","3E","SL","CC","EC","2S").contains(code)||!List.of("GENERAL","TATKAL","LADIES","SENIOR_CITIZEN").contains(q))throw error(HttpStatus.BAD_REQUEST,"Unsupported class or quota");
  List<Long> ids=find(number,date,code,q);
  if(ids.isEmpty()){availability.availability(number,date,code,q);em.flush();ids=find(number,date,code,q);}
  if(ids.isEmpty())throw error(HttpStatus.NOT_FOUND,"Inventory not found");
  return snapshot(lock(ids.getFirst()));
 }
 private List<Long> find(String train,LocalDate date,String cls,String quota){
  return jdbc.queryForList("select i.id from seat_inventories i join trains t on t.id=i.train_id join train_classes c on c.id=i.train_class_id where t.train_number=? and i.journey_date=? and c.class_code=? and i.quota=?",Long.class,train,date,cls,quota);
 }
 private SeatInventory lock(Long id){
  SeatInventory row=inventory.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Inventory not found"));
  em.refresh(row,LockModeType.PESSIMISTIC_WRITE);return row;
 }
 @Transactional
 public AdjustmentResult adjust(AuthPrincipal actor,Long id,String key,AdminInventoryAdjustment request){
  admin(actor);
  if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}")||request==null||!validator.validate(request).isEmpty()||request.reason().trim().length()<5)
   throw error(HttpStatus.BAD_REQUEST,"Supply capacities, current version, a reason (5–500 characters) and a valid Idempotency-Key");
  SeatInventory row=lock(id);
  var old=jdbc.query("select id,requested_capacity,requested_rac_capacity,expected_version,reason from inventory_adjustment_audit where inventory_id=? and actor_id=? and request_key=?",
   (rs,n)->new Retry(rs.getLong(1),rs.getInt(2),rs.getInt(3),rs.getLong(4),rs.getString(5)),id,actor.id(),key);
  if(!old.isEmpty()){
   var receipt=old.getFirst();
   if(receipt.capacity()!=request.totalCapacity()||receipt.rac()!=request.racCapacity()||receipt.version()!=request.expectedVersion()||!receipt.reason().equals(request.reason().trim()))
    throw error(HttpStatus.CONFLICT,"Idempotency key was already used for different adjustment values");
   return new AdjustmentResult(snapshot(row),receipt.id(),true);
  }
  if(row.getVersion()!=request.expectedVersion())throw error(HttpStatus.CONFLICT,"Inventory changed. Refresh and review the adjustment again.");
  if(row.getJourneyDate().isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata"))))throw error(HttpStatus.CONFLICT,"Past journey inventory cannot be adjusted");
  AdminInventoryResponse before=snapshot(row);
  if(request.totalCapacity()>before.physicalCapacity())throw error(HttpStatus.CONFLICT,"Capacity exceeds configured active physical seats");
  if(request.totalCapacity()<before.confirmedReserved()||request.racCapacity()<before.racReserved())
   throw error(HttpStatus.CONFLICT,"Capacity cannot remove confirmed reservations or active holds");
  if(request.totalCapacity()==before.totalCapacity()&&request.racCapacity()==before.racCapacity())
   throw error(HttpStatus.BAD_REQUEST,"No capacity change requested");
  row.adjustCapacities(request.totalCapacity(),request.racCapacity());
  if(request.totalCapacity()>before.totalCapacity()||request.racCapacity()>before.racCapacity())status.promoteAfterInventoryAdjustment(id);
  em.flush();
  AdminInventoryResponse after=snapshot(row);
  jdbc.update("insert into inventory_adjustment_audit(inventory_id,actor_id,request_key,reason,expected_version,requested_capacity,requested_rac_capacity,before_snapshot,after_snapshot) values(?,?,?,?,?,?,?,?,?)",
   id,actor.id(),key,request.reason().trim(),request.expectedVersion(),request.totalCapacity(),request.racCapacity(),encode(before),encode(after));
  long auditId=jdbc.queryForObject("select id from inventory_adjustment_audit where inventory_id=? and actor_id=? and request_key=?",Long.class,id,actor.id(),key);
  events.publishAfterCommit(row.getTrain().getTrainNumber(),row.getJourneyDate(),row.getTrainClass().getClassCode(),row.getQuota(),"ADMIN_INVENTORY_ADJUSTED");
  return new AdjustmentResult(after,auditId,false);
 }
 @Transactional(readOnly=true)
 public AuditPage history(AuthPrincipal actor,Long id,int page,int size){
  admin(actor);if(page<0||page>100000||size<1||size>50)throw error(HttpStatus.BAD_REQUEST,"Invalid audit page");
  if(!inventory.existsById(id))throw error(HttpStatus.NOT_FOUND,"Inventory not found");
  long count=jdbc.queryForObject("select count(*) from inventory_adjustment_audit where inventory_id=?",Long.class,id);
  var entries=jdbc.query("select a.id,a.actor_id,u.full_name,a.reason,a.created_at,a.before_snapshot,a.after_snapshot from inventory_adjustment_audit a join users u on u.id=a.actor_id where inventory_id=? order by a.id desc limit ? offset ?",
   (rs,n)->new AuditEntry(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime().toInstant(ZoneOffset.UTC),decode(rs.getString(6)),decode(rs.getString(7))),id,size,(long)page*size);
  return new AuditPage(entries,page,size,count,(int)((count+size-1)/size));
 }
 private AdminInventoryResponse snapshot(SeatInventory i){
  em.flush();
  int confirmedHeld=held(i.getId(),"CONFIRMED"),racHeld=held(i.getId(),"RAC"),wlHolds=held(i.getId(),"WAITLISTED");
  int confirmed=booked(i.getId(),"CONFIRMED"),rac=booked(i.getId(),"RAC"),wl=booked(i.getId(),"WAITLISTED");
  int physical=jdbc.queryForObject("select count(*) from seats s join coaches c on c.id=s.coach_id where c.train_class_id=? and s.active=true",Integer.class,i.getTrainClass().getId());
  return new AdminInventoryResponse(i.getId(),i.getTrain().getTrainNumber(),i.getTrain().getTrainName(),i.getJourneyDate(),i.getTrainClass().getClassCode(),i.getQuota(),i.getVersion(),
   physical,i.getTotalCapacity(),i.getAvailableCount(),confirmed,confirmedHeld+racHeld,confirmedHeld,racHeld,wlHolds,i.getRacCapacity(),i.getRacAvailableCount(),rac,wl,i.getTotalCapacity()-i.getAvailableCount(),i.getRacCapacity()-i.getRacAvailableCount());
 }
 private int held(Long id,String type){return jdbc.queryForObject("select coalesce(sum(passenger_count),0) from seat_holds where inventory_id=? and status='ACTIVE' and reservation_type=?",Integer.class,id,type);}
 private int booked(Long id,String state){return jdbc.queryForObject("select count(*) from booking_passengers p join bookings b on b.id=p.booking_id join seat_holds h on h.id=b.hold_id where h.inventory_id=? and b.current_status=? and p.cancelled=false",Integer.class,id,state);}
 private String encode(AdminInventoryResponse value){try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException("Could not persist audit snapshot",ex);}}
 private AdminInventoryResponse decode(String value){try{return json.readValue(value,AdminInventoryResponse.class);}catch(JsonProcessingException ex){throw new IllegalStateException("Stored audit snapshot is invalid",ex);}}
 private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
 private record Retry(long id,int capacity,int rac,long version,String reason){}
 public record AdjustmentResult(AdminInventoryResponse inventory,long auditId,boolean replayed){}
 public record AuditEntry(long id,long actorId,String actorName,String reason,Instant createdAt,AdminInventoryResponse before,AdminInventoryResponse after){}
 public record AuditPage(List<AuditEntry> content,int page,int size,long totalElements,int totalPages){}
}
