package com.railyatra.admin;

import com.railyatra.railway.domain.*;
import com.railyatra.railway.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminTrainService {
    private final TrainRepository trains;
    private final TrainStopRepository stops;
    private final StationRepository stations;
    private final TrainScheduleRepository schedules;
    private final TrainClassRepository classes;
    private final JdbcTemplate jdbc;
    private final Validator validator;
    private final EntityManager entityManager;

    public AdminTrainService(TrainRepository trains, TrainStopRepository stops, StationRepository stations,
        TrainScheduleRepository schedules, TrainClassRepository classes, JdbcTemplate jdbc, Validator validator, EntityManager entityManager) {
        this.trains=trains; this.stops=stops; this.stations=stations; this.schedules=schedules;
        this.classes=classes; this.jdbc=jdbc; this.validator=validator; this.entityManager=entityManager;
    }

    @Transactional(readOnly=true)
    public AdminTrainResponse.Page list(String query, Boolean active, int page, int size) {
        if(page<0 || size<1 || size>50 || query.length()>80) throw bad("Invalid page, size or search query");
        var result=trains.adminSearch(query.trim(), active, PageRequest.of(page,size,Sort.by("trainNumber")));
        return new AdminTrainResponse.Page(result.getContent().stream().map(this::response).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public AdminTrainResponse get(Long id) { return response(trains.findById(id).orElseThrow(this::missing)); }

    @Transactional
    public AdminTrainResponse create(AdminTrainRequest request) {
        var route=validate(request);
        String number=normal(request.trainNumber());
        if(trains.existsByTrainNumberIgnoreCase(number)) throw conflict("Train number already exists");
        Train train=trains.saveAndFlush(Train.adminCreated(number, request.name().trim(),request.type()));
        saveRoute(train,request,route);
        schedules.save(new TrainSchedule(train,days(request.runningDays()),LocalDate.now(ZoneId.of("Asia/Kolkata")),null));
        updateClasses(train,request.classes());
        entityManager.flush();
        return response(train);
    }

    @Transactional
    public AdminTrainResponse update(Long id, AdminTrainRequest request) {
        var route=validate(request);
        Train train=trains.findByIdForUpdate(id).orElseThrow(this::missing);
        requireVersion(train,request.version());
        String number=normal(request.trainNumber());
        if(trains.existsByTrainNumberIgnoreCaseAndIdNot(number,id)) throw conflict("Train number already exists");
        var current=response(train);
        boolean routeChanged=!sameRoute(current.stops(), request.stops());
        boolean daysChanged=!new HashSet<>(current.runningDays()).equals(new HashSet<>(request.runningDays()));
        boolean classesChanged=!new HashSet<>(current.classes()).equals(new HashSet<>(request.classes().stream().map(AdminTrainRequest.ClassCode::code).toList()));
        boolean operationalChange=routeChanged || daysChanged || classesChanged || !train.getTrainNumber().equals(number);
        if(operationalChange && current.configurationLocked()) throw conflict("Route, number, running days and classes cannot change while holds are active or after bookings exist. Name and type remain editable.");
        if(daysChanged) {
            var rows=schedules.findByTrainIdOrderByEffectiveFromDesc(id);
            if(rows.size()>1) throw conflict("Multiple schedule periods require a versioned timetable update");
            if(rows.isEmpty()) schedules.save(new TrainSchedule(train,days(request.runningDays()),LocalDate.now(ZoneId.of("Asia/Kolkata")),null));
            else rows.getFirst().setRunsOn(days(request.runningDays()));
        }
        if(routeChanged) {
            stops.deleteRoute(id);
            saveRoute(train,request,route);
            // An endpoint/distance edit must not silently reuse an old segment fare configuration.
            jdbc.update("UPDATE fare_rules SET active=FALSE WHERE train_id=?",id);
        }
        if(classesChanged) updateClasses(train,request.classes());
        train.edit(number,request.name().trim(),request.type());
        // Child-only edits must advance the same revision checked by other admins.
        entityManager.lock(train,jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        return response(train);
    }

    @Transactional
    public AdminTrainResponse deactivate(Long id, Long version) {
        Train train=trains.findByIdForUpdate(id).orElseThrow(this::missing);
        requireVersion(train,version);
        train.deactivate(); entityManager.flush();
        return response(train);
    }

    private List<Station> validate(AdminTrainRequest request) {
        if(request==null || !validator.validate(request).isEmpty()) throw bad("Train details are invalid; check required fields, running days, classes and route stops");
        if(normal(request.source()).equals(normal(request.destination()))) throw bad("Source and destination must differ");
        if(new HashSet<>(request.runningDays()).size()!=request.runningDays().size()) throw bad("Running days must be unique");
        if(new HashSet<>(request.classes()).size()!=request.classes().size()) throw bad("Classes must be unique");
        List<Station> route=new ArrayList<>(); Set<String> visited=new HashSet<>();
        long previousDeparture=-1; int previousDistance=-1;
        for(int i=0;i<request.stops().size();i++) {
            var stop=request.stops().get(i); String code=normal(stop.stationCode()); boolean first=i==0, last=i==request.stops().size()-1;
            if(stop.stopSequence()!=i+1 || !visited.add(code)) throw bad("Stops must be unique and ordered continuously from sequence 1");
            if(first && (!code.equals(normal(request.source())) || stop.dayOffset()!=0 || stop.distanceKm()!=0 || stop.arrival()!=null)) throw bad("First stop must be the source, at day 0 and distance 0, with no arrival time");
            if(last && (!code.equals(normal(request.destination())) || stop.departure()!=null)) throw bad("Last stop must be the destination with no departure time");
            if((!first && stop.arrival()==null) || (!last && stop.departure()==null)) throw bad("Arrival/departure times are required for route stops");
            if(stop.distanceKm()<=previousDistance) throw bad("Route distance must increase at every stop");
            long arrival=stop.dayOffset()*86400L+(stop.arrival()==null?stop.departure():stop.arrival()).toSecondOfDay();
            long departure=stop.dayOffset()*86400L+(stop.departure()==null?stop.arrival():stop.departure()).toSecondOfDay();
            if(arrival<=previousDeparture || departure<arrival) throw bad("Route times must move forward; check day offsets and halt times");
            previousDeparture=departure; previousDistance=stop.distanceKm();
            route.add(stations.findByStationCode(code).filter(Station::isActive).orElseThrow(()->bad("Unknown or inactive station: "+code)));
        }
        return route;
    }
    private void saveRoute(Train train,AdminTrainRequest request,List<Station> route) {
        for(int i=0;i<route.size();i++) { var s=request.stops().get(i); stops.save(new TrainStop(train,route.get(i),i+1,s.arrival(),s.departure(),s.dayOffset(),s.distanceKm())); }
    }
    private void updateClasses(Train train,List<AdminTrainRequest.ClassCode> selected) {
        var existing=classes.findByTrainIdOrderByClassCodeAsc(train.getId());
        Set<String> codes=new HashSet<>(selected.stream().map(AdminTrainRequest.ClassCode::code).toList());
        existing.forEach(cls->cls.setActive(codes.contains(cls.getClassCode())));
        for(var cls:selected) if(existing.stream().noneMatch(row->row.getClassCode().equals(cls.code()))) classes.save(new TrainClass(train,cls.code(),cls.label()));
    }
    private boolean sameRoute(List<AdminTrainResponse.Stop> existing,List<AdminTrainRequest.Stop> supplied) {
        if(existing.size()!=supplied.size()) return false;
        for(int i=0;i<existing.size();i++) {var a=existing.get(i);var b=supplied.get(i);
            if(!a.station().code().equals(normal(b.stationCode())) || a.stopSequence()!=b.stopSequence() || !Objects.equals(a.arrival(),b.arrival()) || !Objects.equals(a.departure(),b.departure()) || a.dayOffset()!=b.dayOffset() || a.distanceKm()!=b.distanceKm()) return false;
        }
        return true;
    }
    private AdminTrainResponse response(Train train) {
        var route=stops.findByTrainIdOrderByStopSequenceAsc(train.getId()).stream().map(s->new AdminTrainResponse.Stop(
            new AdminTrainResponse.Station(s.getStation().getStationCode(),s.getStation().getStationName(),s.getStation().getCity(),s.getStation().getState()),
            s.getStopSequence(),s.getArrivalTime(),s.getDepartureTime(),s.getDayOffset(),s.getDistanceKm())).toList();
        var schedule=schedules.findByTrainIdOrderByEffectiveFromDesc(train.getId());
        String bits=schedule.isEmpty()?"0000000":schedule.getFirst().getRunsOn();
        List<DayOfWeek> days=new ArrayList<>(); for(var day:DayOfWeek.values()) if(bits.charAt(day.getValue()-1)=='1') days.add(day);
        boolean locked=jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE train_id=?",Long.class,train.getId())>0 ||
            jdbc.queryForObject("SELECT COUNT(*) FROM seat_holds WHERE train_id=? AND status='ACTIVE' AND expires_at>?",Long.class,train.getId(),LocalDateTime.ofInstant(Instant.now(),ZoneOffset.UTC))>0;
        return new AdminTrainResponse(train.getId(),train.getTrainNumber(),train.getTrainName(),train.getTrainType(),train.isActive(),train.getVersion(),train.getDataSource(),
            route.isEmpty()?null:route.getFirst().station().code(),route.isEmpty()?null:route.getLast().station().code(),days,
            classes.findByTrainIdOrderByClassCodeAsc(train.getId()).stream().filter(TrainClass::isActive).map(TrainClass::getClassCode).toList(),route,locked);
    }
    private String days(List<DayOfWeek> selected) {StringBuilder bits=new StringBuilder(); for(var day:DayOfWeek.values()) bits.append(selected.contains(day)?'1':'0'); return bits.toString();}
    private String normal(String text){return text.trim().toUpperCase(Locale.ROOT);}
    private void requireVersion(Train train,Long version){if(version==null || version!=train.getVersion())throw conflict("This train changed since you opened it. Reload before saving.");}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Train not found");}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
