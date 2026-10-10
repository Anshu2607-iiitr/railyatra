package com.railyatra.admin;

import com.railyatra.auth.*;
import com.railyatra.booking.*;
import com.railyatra.passenger.*;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.repository.*;
import com.railyatra.railway.service.SeatAllocationService;
import com.railyatra.railway.realtime.AvailabilityEventPublisher;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties={"railyatra.seed.enabled=false","railyatra.refunds.worker-enabled=false","railyatra.holds.expiry-check-ms=600000",
 "spring.datasource.url=jdbc:h2:mem:inventory_admin_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AdminInventoryServiceTest {
 @Autowired AdminInventoryService service; @Autowired JdbcTemplate jdbc; @Autowired EntityManager em;
 @Autowired UserRepository users; @Autowired TrainRepository trains; @Autowired TrainClassRepository classes;
 @Autowired CoachRepository coaches; @Autowired SeatRepository seats; @Autowired SeatInventoryRepository inventories;
 @Autowired SeatHoldRepository holds; @Autowired StationRepository stations; @Autowired BookingRepository bookings;
 @Autowired BookingPassengerRepository passengers; @Autowired SeatAllocationService allocations;
 @Autowired PlatformTransactionManager manager; @Autowired MockMvc mvc;
 @MockitoSpyBean AvailabilityEventPublisher events;
 private Fixture fixture(){return new TransactionTemplate(manager).execute(tx->{
  String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(Locale.ROOT);
  var user=users.saveAndFlush(new User("Inventory admin","inv"+suffix+"@test.local","I"+suffix,"hash"));
  jdbc.update("update users set role='ROLE_ADMIN' where id=?",user.getId());em.refresh(user);
  var train=trains.save(new Train("I"+suffix,"Inventory test"));
  var cls=classes.save(new TrainClass(train,"3A","AC 3 Tier"));
  var coach=coaches.save(new Coach(cls,"B1",1));for(int n=1;n<=8;n++)seats.save(new Seat(coach,""+n,SeatType.LOWER));
  var origin=stations.save(new Station("O"+suffix,"Origin","City","State"));
  var end=stations.save(new Station("D"+suffix,"Destination","City","State"));
  var inventory=inventories.saveAndFlush(new SeatInventory(train,cls,LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2),"GENERAL",4,2));
  inventory.reserveConfirmedSeats(1);
  var cnf=book(user,inventory,origin,end,HoldReservationType.CONFIRMED,BookingStatus.CONFIRMED);
  var assigned=allocations.allocateFromHold(cnf.getHold(),cnf.getInternalBookingReference(),List.of(person()));
  passengers.save(new BookingPassenger(cnf,0,person(),assigned.assignments().getFirst()));
  inventory.reserveHoldSeats(1);holds.save(hold(user,inventory,HoldReservationType.CONFIRMED));
  inventory.reserveRacSeats(1);var rac=book(user,inventory,origin,end,HoldReservationType.RAC,BookingStatus.RAC);passengers.save(new BookingPassenger(rac,0,person(),null));
  inventory.reserveRacSeats(1);holds.save(hold(user,inventory,HoldReservationType.RAC));
  inventory.addWaitingList(1);var wl=book(user,inventory,origin,end,HoldReservationType.WAITLISTED,BookingStatus.WAITLISTED);passengers.save(new BookingPassenger(wl,0,person(),null));
  em.flush();
  return new Fixture(new AuthPrincipal(user.getId(),user.getEmail(),"ROLE_ADMIN"),inventory.getId(),train.getTrainNumber(),inventory.getJourneyDate(),cnf.getId());
 });}
 private SeatHold hold(User u,SeatInventory i,HoldReservationType type){return new SeatHold(u.getId(),i.getTrain(),i.getTrainClass(),i.getJourneyDate(),i.getQuota(),1,i,UUID.randomUUID().toString(),Instant.now().plusSeconds(600),type);}
 private Booking book(User u,SeatInventory i,Station a,Station b,HoldReservationType type,BookingStatus status){
  SeatHold hold=holds.save(hold(u,i,type));hold.consume();
  var booking=new Booking(u.getId(),hold,a,b,"RY"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(Locale.ROOT),UUID.randomUUID().toString(),new BigDecimal("500.00"));
  booking.markPaid("MOCK"+UUID.randomUUID(),status,status==BookingStatus.CONFIRMED?null:1);return bookings.saveAndFlush(booking);
 }
 private PassengerDetailsRequest person(){return new PassengerDetailsRequest("Inventory Passenger",30,Gender.FEMALE,BerthPreference.NO_PREFERENCE);}
 private AdminInventoryResponse inspect(Fixture f){return service.inspect(f.admin(),f.train(),f.date(),"3A","GENERAL");}
 private AdminInventoryAdjustment request(AdminInventoryResponse s,int capacity,int rac){return new AdminInventoryAdjustment(capacity,rac,s.version(),"Operational capacity adjustment");}
 @Test void snapshotAndReductionPreserveConfirmedSeatAndHolds(){
  var f=fixture();var before=inspect(f);
  assertEquals(1,before.confirmed());assertEquals(2,before.held());assertEquals(1,before.rac());assertEquals(1,before.waitingList());assertEquals(2,before.available());
  String seat=jdbc.queryForObject("select seat_number from booking_passengers where booking_id=?",String.class,f.booking());
  var after=service.adjust(f.admin(),f.id(),"reduce-capacity",request(before,2,2)).inventory();
  assertEquals(0,after.available());assertEquals(1,after.confirmed());assertEquals(2,after.held());
  assertEquals(seat,jdbc.queryForObject("select seat_number from booking_passengers where booking_id=?",String.class,f.booking()));
  var history=service.history(f.admin(),f.id(),0,10);assertEquals(1,history.totalElements());assertEquals(before,history.content().getFirst().before());assertEquals(after,history.content().getFirst().after());
 }
 @Test void unsafeReductionsAndPhysicalOverbookingAreRejected(){
  var f=fixture();var before=inspect(f);
  for(int[] values:List.of(new int[]{1,2},new int[]{4,1},new int[]{9,2})){
   assertEquals(409,assertThrows(ResponseStatusException.class,()->service.adjust(f.admin(),f.id(),UUID.randomUUID().toString(),request(before,values[0],values[1]))).getStatusCode().value());
  }
  assertEquals(0,service.history(f.admin(),f.id(),0,10).totalElements());assertEquals(before,inspect(f));
 }
 @Test void capacityIncreasePromotesQueuesWithoutChangingExistingSeat(){
  var f=fixture();var before=inspect(f);
  String seat=jdbc.queryForObject("select seat_number from booking_passengers where booking_id=?",String.class,f.booking());
  var after=service.adjust(f.admin(),f.id(),"increase-capacity",request(before,8,3)).inventory();
  assertEquals(3,after.confirmed());assertEquals(0,after.rac());assertEquals(0,after.waitingList());assertEquals(2,after.held());assertEquals(4,after.available());
  assertEquals(seat,jdbc.queryForObject("select seat_number from booking_passengers where booking_id=?",String.class,f.booking()));
  assertEquals(3,jdbc.queryForObject("select count(*) from seat_allocations where seat_inventory_id=?",Integer.class,f.id()));
 }
 @Test void retriesAreIdempotentAndVersionConflictsRequireReview(){
  var f=fixture();var before=inspect(f);var request=request(before,3,2);
  var first=service.adjust(f.admin(),f.id(),"repeat-adjustment",request);
  var second=service.adjust(f.admin(),f.id(),"repeat-adjustment",request);
  assertTrue(second.replayed());assertEquals(first.auditId(),second.auditId());assertEquals(1,service.history(f.admin(),f.id(),0,10).totalElements());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.adjust(f.admin(),f.id(),"repeat-adjustment",request(before,2,2))).getStatusCode().value());
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.adjust(f.admin(),f.id(),"stale-adjustment",request(before,2,2))).getStatusCode().value());
 }
 @Test void rollbackRemovesAuditAndPublishesNoAvailabilityEvent(){
  var f=fixture();var before=inspect(f);clearInvocations(events);
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(tx->{
   service.adjust(f.admin(),f.id(),"rollback-adjustment",request(before,3,2));throw new IllegalStateException("force rollback");
  }));
  assertEquals(before,inspect(f));assertEquals(0,service.history(f.admin(),f.id(),0,10).totalElements());
  verify(events,never()).publish(eq(f.train()),eq(f.date()),eq("3A"),eq("GENERAL"),eq("ADMIN_INVENTORY_ADJUSTED"));
  service.adjust(f.admin(),f.id(),"committed-adjustment",request(before,3,2));
  verify(events,times(1)).publish(eq(f.train()),eq(f.date()),eq("3A"),eq("GENERAL"),eq("ADMIN_INVENTORY_ADJUSTED"));
 }
 @Test void concurrentAdminsCannotApplyTheSameStaleRevisionTwice()throws Exception{
  var f=fixture();var before=inspect(f);var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
  try{
   Callable<Boolean> work=()->{gate.await(10,TimeUnit.SECONDS);try{service.adjust(f.admin(),f.id(),UUID.randomUUID().toString(),request(before,2,2));return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
   var a=pool.submit(work);var b=pool.submit(work);
   assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
  assertEquals(1,service.history(f.admin(),f.id(),0,10).totalElements());assertEquals(0,inspect(f).available());assertEquals(1,inspect(f).confirmed());
 }
 @Test void endpointsRequireAuthenticatedDatabaseAdmin()throws Exception{
  var f=fixture();String path="/api/admin/inventory?trainNumber="+f.train()+"&date="+f.date()+"&class=3A";
  mvc.perform(get(path)).andExpect(status().is4xxClientError());
  mvc.perform(get(path).with(user("regular").roles("USER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/inventory/"+f.id()+"/adjustments").with(user("regular").roles("USER")).header("Idempotency-Key","denied-adjustment").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  var authentication=new UsernamePasswordAuthenticationToken(f.admin(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  mvc.perform(get(path).with(authentication(authentication))).andExpect(status().isOk()).andExpect(jsonPath("$.held").value(2));
  jdbc.update("update users set role='ROLE_USER' where id=?",f.admin().id());
  mvc.perform(get(path).with(authentication(authentication))).andExpect(status().isForbidden());
 }
 private record Fixture(AuthPrincipal admin,Long id,String train,LocalDate date,String booking){}
}
