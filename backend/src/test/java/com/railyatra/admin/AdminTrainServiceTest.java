package com.railyatra.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railyatra.auth.*;
import com.railyatra.booking.Booking;
import com.railyatra.booking.BookingRepository;
import com.railyatra.passenger.*;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.dto.CreateSeatHoldRequest;
import com.railyatra.railway.repository.*;
import com.railyatra.railway.service.AvailabilityService;
import com.railyatra.railway.service.SeatHoldService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties={"railyatra.seed.enabled=false","railyatra.refunds.worker-enabled=false","railyatra.holds.expiry-check-ms=600000",
    "spring.datasource.url=jdbc:h2:mem:admin_train_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @Transactional
class AdminTrainServiceTest {
    @Autowired AdminTrainService service;
    @Autowired StationRepository stations;
    @Autowired TrainRepository trains;
    @Autowired TrainClassRepository classes;
    @Autowired TrainScheduleRepository schedules;
    @Autowired SeatInventoryRepository inventories;
    @Autowired SeatHoldRepository holds;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired AvailabilityService availability;
    @Autowired SeatHoldService holdService;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @BeforeEach void stations() {
        stations.save(new Station("ATONE","Admin origin","City","State"));
        stations.save(new Station("ATMID","Admin middle","City","State"));
        stations.save(new Station("ATEND","Admin destination","City","State"));
    }
    private AdminTrainRequest request(String number,Long version) {
        return new AdminTrainRequest(number,"Admin Test Express",TrainType.EXPRESS,"ATONE","ATEND",
            List.of(DayOfWeek.values()),List.of(AdminTrainRequest.ClassCode.SL),List.of(
                new AdminTrainRequest.Stop("ATONE",1,null,LocalTime.of(8,0),0,0),
                new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(10,0),LocalTime.of(10,5),0,100),
                new AdminTrainRequest.Stop("ATEND",3,LocalTime.of(14,0),null,0,300)),version);
    }
    @Test void createEditIntermediateSearchAndDeactivateUseSavedState() {
        var created=service.create(request("AD0001",null));
        assertEquals("LOCAL_ADMIN",created.dataSource());assertEquals(3,created.stops().size());
        assertEquals(List.of("SL"),created.classes());
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        assertEquals(1,trains.searchByOrderedStops("ATMID","ATEND",date,date.getDayOfWeek().getValue(),"SL","TRAIN_NUMBER",PageRequest.of(0,10)).getTotalElements());
        assertEquals(0,trains.searchByOrderedStops("ATEND","ATMID",date,date.getDayOfWeek().getValue(),"SL","TRAIN_NUMBER",PageRequest.of(0,10)).getTotalElements());
        var r=request("AD0002",created.version());
        var edited=service.update(created.id(),new AdminTrainRequest(r.trainNumber(),"Renamed train",TrainType.SUPERFAST,r.source(),r.destination(),r.runningDays(),List.of(AdminTrainRequest.ClassCode.THIRD_AC),r.stops(),r.version()));
        assertTrue(edited.version()>created.version());assertEquals("Renamed train",service.get(created.id()).name());
        assertEquals(TrainType.SUPERFAST,edited.type()); assertEquals(List.of("3A"),edited.classes());
        assertFalse(classes.findByTrainIdAndClassCode(created.id(),"SL").orElseThrow().isActive());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(created.id(),r)).getStatusCode().value());
        var inactive=service.deactivate(edited.id(),edited.version());assertFalse(inactive.active());
        assertEquals(0,trains.searchByOrderedStops("ATONE","ATEND",date,date.getDayOfWeek().getValue(),"3A","TRAIN_NUMBER",PageRequest.of(0,10)).getTotalElements());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->availability.availability("AD0002",date,"3A","GENERAL")).getStatusCode().value());
        var passengers=List.of(new PassengerDetailsRequest("Test passenger",30,Gender.MALE,BerthPreference.NO_PREFERENCE));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->holdService.create(new AuthPrincipal(1L,"any@test.local","ROLE_USER"),"inactive-test",new CreateSeatHoldRequest("AD0002",date,"3A","GENERAL",passengers))).getStatusCode().value());
    }
    @Test void duplicateNumberAndInvalidRoutesAreRejected() {
        service.create(request("AD0001",null));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.create(request("ad0001",null))).getStatusCode().value());
        var r=request("AD0002",null);
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.source(),r.runningDays(),r.classes(),r.stops(),null));
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),List.of(),r.classes(),r.stops(),null));
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),r.runningDays(),r.classes(),List.of(r.stops().getFirst()),null));
        var invalid=new ArrayList<>(r.stops()); invalid.set(1,new AdminTrainRequest.Stop("ATMID",3,LocalTime.of(10,0),LocalTime.of(10,5),0,100));
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),r.runningDays(),r.classes(),invalid,null));
        invalid.set(1,new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(7,0),LocalTime.of(7,5),0,100));
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),r.runningDays(),r.classes(),invalid,null));
        invalid.set(1,new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(10,0),LocalTime.of(10,5),0,400));
        assertBad(new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),r.runningDays(),r.classes(),invalid,null));
        assertEquals(1,trains.count());
    }
    @Test void activeHoldsAndBookingsProtectOperationalFieldsButNotNameOrType() {
        var created=service.create(request("AD0001",null));var train=trains.findById(created.id()).orElseThrow();
        var cls=classes.findByTrainIdAndClassCode(train.getId(),"SL").orElseThrow();
        var owner=users.save(new User("Owner","admin-train-owner@test.local","9000000098","test-hash"));
        var inventory=inventories.save(new SeatInventory(train,cls,LocalDate.now().plusDays(1),"GENERAL",8,1));
        var hold=holds.saveAndFlush(new SeatHold(owner.getId(),train,cls,inventory.getJourneyDate(),"GENERAL",1,inventory,"protected-hold",Instant.now().plusSeconds(600),HoldReservationType.CONFIRMED));
        assertTrue(service.get(train.getId()).configurationLocked());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(train.getId(),request("AD0002",created.version()))).getStatusCode().value());
        var r=request("AD0001",created.version());
        var renamed=service.update(train.getId(),new AdminTrainRequest(r.trainNumber(),"Safe metadata update",TrainType.MAIL,r.source(),r.destination(),r.runningDays(),r.classes(),r.stops(),r.version()));
        assertEquals("Safe metadata update",renamed.name());
        hold.consume(); holds.flush();
        bookings.saveAndFlush(new Booking(owner.getId(),hold,stations.findByStationCode("ATONE").orElseThrow(),stations.findByStationCode("ATEND").orElseThrow(),"RYADMINTEST","test-booking",BigDecimal.TEN));
        assertTrue(service.get(train.getId()).configurationLocked());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.update(train.getId(),request("AD0002",renamed.version()))).getStatusCode().value());
        assertFalse(service.deactivate(train.getId(),renamed.version()).active());
        assertEquals(1,bookings.count());
    }
    @Test void scheduleChangesApplyToSearchAndDirectAvailabilityChecks() {
        var created=service.create(request("AD0001",null));var r=request("AD0001",created.version());
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        var onlyOtherDay=List.of(date.getDayOfWeek().plus(1));
        var updated=service.update(created.id(),new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),onlyOtherDay,r.classes(),r.stops(),r.version()));
        assertEquals(onlyOtherDay,updated.runningDays());
        assertEquals(0,trains.searchByOrderedStops("ATONE","ATEND",date,date.getDayOfWeek().getValue(),"SL","TRAIN_NUMBER",PageRequest.of(0,10)).getTotalElements());
        assertEquals("This train does not run on the selected date",assertThrows(ResponseStatusException.class,()->availability.availability("AD0001",date,"SL","GENERAL")).getReason());
    }
    @Test void everyAdminEndpointRejectsRegularUsersAndAnonymousCallers() throws Exception {
        String body=json.writeValueAsString(request("AD0001",null));
        for(String path:List.of("/api/admin/trains","/api/admin/trains/options","/api/admin/trains/1")) {
            mvc.perform(get(path)).andExpect(status().is4xxClientError());
            mvc.perform(get(path).with(user("traveller").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/trains").with(user("traveller").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/trains/1").with(user("traveller").roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/trains/1/deactivate").with(user("traveller").roles("USER")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/trains").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("ATONE"));
        mvc.perform(post("/api/admin/trains").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body.replace("EXPRESS","UNSUPPORTED")))
            .andExpect(status().isBadRequest());
    }

    @Test void visualRouteReorderPersistsAndReloadsThroughAdminApi() throws Exception {
        stations.saveAndFlush(new Station("ATEXTRA","Extra stop","City","State"));
        var initial=request("ROUTE01",null);
        var route=List.of(
            new AdminTrainRequest.Stop("ATONE",1,null,LocalTime.of(22,0),0,0),
            new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(23,0),LocalTime.of(23,5),0,100),
            new AdminTrainRequest.Stop("ATEXTRA",3,LocalTime.of(0,10),LocalTime.of(0,15),1,200),
            new AdminTrainRequest.Stop("ATEND",4,LocalTime.of(3,0),null,1,400));
        var request=new AdminTrainRequest(initial.trainNumber(),initial.name(),initial.type(),initial.source(),initial.destination(),initial.runningDays(),initial.classes(),route,null);
        String saved=mvc.perform(post("/api/admin/trains").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.stops[2].dayNumber").value(2))
            .andExpect(jsonPath("$.stops[1].haltMinutes").value(5)).andReturn().getResponse().getContentAsString();
        long id=json.readTree(saved).get("id").asLong(),version=json.readTree(saved).get("version").asLong();
        entityManager.flush();entityManager.clear();
        var reordered=List.of(route.get(0),
            new AdminTrainRequest.Stop("ATEXTRA",2,LocalTime.of(23,0),LocalTime.of(23,5),0,100),
            new AdminTrainRequest.Stop("ATMID",3,LocalTime.of(0,10),LocalTime.of(0,15),1,200),route.get(3));
        var updated=new AdminTrainRequest(initial.trainNumber(),initial.name(),initial.type(),initial.source(),initial.destination(),initial.runningDays(),initial.classes(),reordered,version);
        mvc.perform(put("/api/admin/trains/"+id).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isOk());
        entityManager.flush();entityManager.clear();
        mvc.perform(get("/api/admin/trains/"+id).with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("ATONE"))
            .andExpect(jsonPath("$.destination").value("ATEND"))
            .andExpect(jsonPath("$.stops[0].stopSequence").value(1))
            .andExpect(jsonPath("$.stops[1].station.code").value("ATEXTRA"))
            .andExpect(jsonPath("$.stops[1].stopSequence").value(2))
            .andExpect(jsonPath("$.stops[2].station.code").value("ATMID"))
            .andExpect(jsonPath("$.stops[2].dayNumber").value(2))
            .andExpect(jsonPath("$.stops[2].haltMinutes").value(5))
            .andExpect(jsonPath("$.stops[3].distanceKm").value(400));
        mvc.perform(put("/api/admin/trains/"+id).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isConflict());
    }

    @Test void routeApiRejectsEndpointSequenceDistanceAndTimeViolations() throws Exception {
        var r=request("ROUTEBAD",null);
        List<List<AdminTrainRequest.Stop>> invalidRoutes=List.of(
            List.of(new AdminTrainRequest.Stop("ATMID",1,null,LocalTime.of(8,0),0,0),r.stops().get(1),r.stops().get(2)),
            List.of(r.stops().get(0),r.stops().get(1),new AdminTrainRequest.Stop("ATMID",3,LocalTime.of(14,0),null,0,300)),
            List.of(r.stops().get(0),new AdminTrainRequest.Stop("ATMID",1,LocalTime.of(10,0),LocalTime.of(10,5),0,100),r.stops().get(2)),
            List.of(r.stops().get(0),new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(10,0),LocalTime.of(10,5),0,0),r.stops().get(2)),
            List.of(r.stops().get(0),new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(23,55),LocalTime.of(0,5),0,100),r.stops().get(2)),
            List.of(r.stops().get(0),new AdminTrainRequest.Stop("ATMID",2,LocalTime.of(10,0),LocalTime.of(10,5),1,100),r.stops().get(2)),
            List.of(r.stops().get(0),new AdminTrainRequest.Stop("ATMID",2,null,LocalTime.of(10,5),0,100),r.stops().get(2)));
        for(var route:invalidRoutes) {
            var invalid=new AdminTrainRequest(r.trainNumber(),r.name(),r.type(),r.source(),r.destination(),r.runningDays(),r.classes(),route,null);
            mvc.perform(post("/api/admin/trains").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        }
        assertEquals(0,trains.count());
    }

    private void assertBad(AdminTrainRequest request) { assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(request)).getStatusCode().value()); }
}
