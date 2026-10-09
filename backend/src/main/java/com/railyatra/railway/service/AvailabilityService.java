package com.railyatra.railway.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.railyatra.railway.domain.*;
import com.railyatra.railway.dto.AvailabilityResponse;
import com.railyatra.railway.repository.*;
import com.railyatra.railway.realtime.AvailabilityEventPublisher;

@Service
public class AvailabilityService {
    private final TrainRepository trains;
    private final TrainClassRepository classes;
    private final SeatRepository seats;
    private final SeatInventoryRepository inventory;
    private final AvailabilityEventPublisher events;
    private final TrainScheduleRepository schedules;
    private final int horizon;
    public AvailabilityService(TrainRepository trains,TrainClassRepository classes,SeatRepository seats,
        SeatInventoryRepository inventory,AvailabilityEventPublisher events,TrainScheduleRepository schedules,@Value("${railyatra.inventory.booking-horizon-days:120}") int horizon) {
        this.trains=trains;this.classes=classes;this.seats=seats;this.inventory=inventory;this.events=events;this.schedules=schedules;this.horizon=horizon;
    }
    @Transactional
    public AvailabilityResponse availability(String number,LocalDate date,String classCode,String quota) {
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));
        if(date.isBefore(today)||date.isAfter(today.plusDays(horizon))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Journey date is outside the configured booking horizon");
        // Same lock as admin edits and holds also serializes lazy inventory initialization.
        Train train=trains.findByTrainNumberForUpdate(number).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Train not found"));
        if(!train.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,"This train is deactivated");
        boolean running=schedules.findByTrainIdOrderByEffectiveFromDesc(train.getId()).stream().anyMatch(schedule->
            !date.isBefore(schedule.getEffectiveFrom()) && (schedule.getEffectiveTo()==null || !date.isAfter(schedule.getEffectiveTo()))
            && schedule.getRunsOn().charAt(date.getDayOfWeek().getValue()-1)=='1');
        if(!running) throw new ResponseStatusException(HttpStatus.CONFLICT,"This train does not run on the selected date");
        TrainClass tc=classes.findByTrainIdAndClassCode(train.getId(),classCode.toUpperCase(Locale.ROOT)).filter(TrainClass::isActive)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Class is not available on this train"));
        String q=quota==null||quota.isBlank()?"GENERAL":quota.toUpperCase(Locale.ROOT).replace(' ','_');
        SeatInventory row=inventory.findByTrainIdAndJourneyDateAndTrainClassIdAndQuota(train.getId(),date,tc.getId(),q).orElseGet(()->{
            int cap=Math.toIntExact(seats.countByCoachTrainClassId(tc.getId()));
            if(cap==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Coach and seat configuration is required before reservations can open for this class");
            SeatInventory created=inventory.save(new SeatInventory(train,tc,date,q,cap,Math.max(1,cap/10)));
            events.publishAfterCommit(number,date,classCode,q,"INVENTORY_CREATED");return created;
        });
        if(row.getAvailableCount()>0)return response("AVAILABLE",row.getAvailableCount(),number,classCode,q,train.getDataSource());
        if(row.getRacAvailableCount()>0)return response("RAC",row.getRacAvailableCount(),number,classCode,q,train.getDataSource());
        return response("WL",row.getWaitingListCount(),number,classCode,q,train.getDataSource());
    }
    public void publishAvailabilityChanged(String train,LocalDate date,String cls,String quota,String reason){events.publishAfterCommit(train,date,cls,quota,reason);}
    private AvailabilityResponse response(String status,int count,String train,String cls,String quota,String source){return new AvailabilityResponse(status,count,status+" "+count,train,cls,quota,source);}
}
