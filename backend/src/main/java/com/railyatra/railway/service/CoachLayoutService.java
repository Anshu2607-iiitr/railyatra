package com.railyatra.railway.service;

import com.railyatra.railway.domain.Coach;
import com.railyatra.railway.domain.CoachLayoutSlot;
import com.railyatra.railway.domain.CoachLayoutTemplate;
import com.railyatra.railway.domain.Seat;
import com.railyatra.railway.domain.TrainClass;
import com.railyatra.railway.repository.CoachLayoutSlotRepository;
import com.railyatra.railway.repository.CoachLayoutTemplateRepository;
import com.railyatra.railway.repository.CoachRepository;
import com.railyatra.railway.repository.SeatRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CoachLayoutService {
    private final CoachLayoutTemplateRepository templates;
    private final CoachLayoutSlotRepository slots;
    private final CoachRepository coaches;
    private final SeatRepository seats;

    public CoachLayoutService(CoachLayoutTemplateRepository templates, CoachLayoutSlotRepository slots, CoachRepository coaches, SeatRepository seats) {
        this.templates = templates; this.slots = slots; this.coaches = coaches; this.seats = seats;
    }

    /** Materializes physical seat identities from one reusable layout definition. */
    @Transactional
    public Coach createCoach(TrainClass trainClass, String coachCode, int coachPosition, String templateCode) {
        CoachLayoutTemplate template = templates.findByTemplateCodeAndActiveTrue(templateCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown active coach layout template"));
        if (!template.getClassCode().equalsIgnoreCase(trainClass.getClassCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coach layout template does not match train class");
        }
        List<CoachLayoutSlot> layoutSlots = slots.findByTemplateIdOrderByPositionIndexAsc(template.getId());
        if (layoutSlots.isEmpty()) throw new IllegalStateException("Coach layout template has no seats");
        Coach coach = coaches.save(new Coach(trainClass, template, coachCode, coachPosition));
        seats.saveAll(layoutSlots.stream().map(slot -> new Seat(coach, slot.getSeatNumber(), slot.getSeatType())).toList());
        return coach;
    }
}
