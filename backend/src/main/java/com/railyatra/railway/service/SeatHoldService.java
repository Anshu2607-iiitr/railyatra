package com.railyatra.railway.service;

import com.railyatra.auth.AuthPrincipal;
import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.SeatHoldStatus;
import com.railyatra.railway.domain.SeatInventory;
import com.railyatra.railway.domain.HoldReservationType;
import com.railyatra.railway.domain.Train;
import com.railyatra.railway.domain.TrainClass;
import com.railyatra.railway.dto.CreateSeatHoldRequest;
import com.railyatra.railway.dto.SeatHoldResponse;
import com.railyatra.railway.repository.SeatHoldRepository;
import com.railyatra.railway.repository.SeatInventoryRepository;
import com.railyatra.railway.repository.TrainClassRepository;
import com.railyatra.railway.repository.TrainRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatHoldService {
    private final SeatHoldRepository holds;
    private final SeatInventoryRepository inventories;
    private final TrainRepository trains;
    private final TrainClassRepository classes;
    private final AvailabilityService availability;
    private final int holdDurationSeconds;

    public SeatHoldService(SeatHoldRepository holds, SeatInventoryRepository inventories, TrainRepository trains, TrainClassRepository classes,
                           AvailabilityService availability, @Value("${railyatra.holds.duration-seconds:600}") int holdDurationSeconds) {
        if (holdDurationSeconds <= 0) throw new IllegalStateException("RAILYATRA_HOLD_DURATION_SECONDS must be positive");
        this.holds = holds; this.inventories = inventories; this.trains = trains; this.classes = classes; this.availability = availability; this.holdDurationSeconds = holdDurationSeconds;
    }

    @Transactional
    public SeatHoldResponse create(AuthPrincipal principal, String requestKey, CreateSeatHoldRequest request) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key is required");
        SeatHold existing = existingForRequest(principal.id(), requestKey);
        if (existing != null) return SeatHoldResponse.from(existing);
        // Admin changes and new checkout holds serialize on the same database train row.
        Train train = trains.findByTrainNumberForUpdate(request.trainNumber().trim()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found"));
        // Recheck retries before taking an inventory lock: booking locks hold -> inventory.
        existing = existingForRequest(principal.id(), requestKey);
        if (existing != null) return SeatHoldResponse.from(existing);
        if (!train.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This train is deactivated and no longer accepts new reservations");
        TrainClass trainClass = classes.findByTrainIdAndClassCode(train.getId(), request.classCode().trim().toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class is not available on this train"));
        if (!trainClass.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This class is no longer offered on this train");
        String quota = normaliseQuota(request.quota());
        availability.availability(train.getTrainNumber(), request.journeyDate(), trainClass.getClassCode(), quota);
        SeatInventory inventory = inventories.findByTrainIdAndJourneyDateAndTrainClassIdAndQuota(train.getId(), request.journeyDate(), trainClass.getId(), quota)
            .flatMap(found -> inventories.findByIdForUpdate(found.getId()))
            .orElseThrow(() -> new IllegalStateException("Inventory could not be initialized"));
        HoldReservationType reservationType;
        if (inventory.getAvailableCount() >= request.passengers().size()) { inventory.reserveHoldSeats(request.passengers().size()); reservationType = HoldReservationType.CONFIRMED; }
        else if (inventory.getRacAvailableCount() >= request.passengers().size()) { inventory.reserveRacSeats(request.passengers().size()); reservationType = HoldReservationType.RAC; }
        else { reservationType = HoldReservationType.WAITLISTED; }
        SeatHold hold = holds.save(new SeatHold(principal.id(), train, trainClass, request.journeyDate(), quota, request.passengers().size(), inventory, requestKey, Instant.now().plusSeconds(holdDurationSeconds), reservationType));
        availability.publishAvailabilityChanged(train.getTrainNumber(), request.journeyDate(), trainClass.getClassCode(), quota, "HOLD_CREATED");
        return SeatHoldResponse.from(hold);
    }

    @Transactional
    public SeatHoldResponse getForUser(AuthPrincipal principal, String holdId) {
        SeatHold hold = lockOwned(principal, holdId);
        expireIfNeeded(hold, Instant.now());
        return SeatHoldResponse.from(hold);
    }

    @Transactional
    public void releaseForUser(AuthPrincipal principal, String holdId) {
        SeatHold hold = lockOwned(principal, holdId);
        expireIfNeeded(hold, Instant.now());
        if (hold.getStatus() == SeatHoldStatus.ACTIVE) releaseActive(hold, SeatHoldStatus.RELEASED, "HOLD_RELEASED");
    }

    /** Used only by the future transactional payment/booking flow. */
    @Transactional
    public SeatHold consumeActiveHold(Long userId, String holdId) {
        SeatHold hold = holds.findByIdForUpdate(holdId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat hold not found"));
        if (!hold.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This seat hold belongs to another user");
        expireIfNeeded(hold, Instant.now());
        if (hold.getStatus() != SeatHoldStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat hold is no longer active");
        hold.consume();
        return hold;
    }

    /** Locks and verifies a hold while leaving it ACTIVE until a booking transaction commits. */
    @Transactional
    public SeatHold lockActiveHoldForBooking(Long userId, String holdId) {
        SeatHold hold = holds.findByIdForUpdate(holdId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat hold not found"));
        if (!hold.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This seat hold belongs to another user");
        expireIfNeeded(hold, Instant.now());
        return hold;
    }

    public void consumeLockedHold(SeatHold hold) {
        if (hold.getStatus() != SeatHoldStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat hold is no longer active");
        hold.consume();
    }

    public void releaseLockedHold(SeatHold hold, String reason) {
        if (hold.getStatus() == SeatHoldStatus.ACTIVE) releaseActive(hold, SeatHoldStatus.RELEASED, reason);
    }

    @Transactional
    public void expireHold(String holdId) {
        holds.findByIdForUpdate(holdId).ifPresent(hold -> expireIfNeeded(hold, Instant.now()));
    }

    private SeatHold lockOwned(AuthPrincipal principal, String holdId) {
        SeatHold hold = holds.findByIdForUpdate(holdId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat hold not found"));
        if (!hold.getUserId().equals(principal.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat hold not found");
        return hold;
    }
    private SeatHold existingForRequest(Long userId, String requestKey) {
        return holds.findByUserIdAndRequestKey(userId, requestKey).map(found -> {
            SeatHold locked = holds.findByIdForUpdate(found.getId()).orElseThrow();
            expireIfNeeded(locked, Instant.now());
            return locked;
        }).orElse(null);
    }
    private void expireIfNeeded(SeatHold hold, Instant now) {
        if (hold.isExpired(now)) releaseActive(hold, SeatHoldStatus.EXPIRED, "HOLD_EXPIRED");
    }
    private void releaseActive(SeatHold hold, SeatHoldStatus target, String reason) {
        SeatInventory inventory = inventories.findByIdForUpdate(hold.getInventory().getId()).orElseThrow(() -> new IllegalStateException("Hold inventory is missing"));
        if (hold.getReservationType() == HoldReservationType.CONFIRMED) inventory.releaseHoldSeats(hold.getPassengerCount());
        else if (hold.getReservationType() == HoldReservationType.RAC) inventory.releaseRacSeats(hold.getPassengerCount());
        if (target == SeatHoldStatus.EXPIRED) hold.expire(); else hold.release();
        availability.publishAvailabilityChanged(inventory.getTrain().getTrainNumber(), inventory.getJourneyDate(), inventory.getTrainClass().getClassCode(), inventory.getQuota(), reason);
    }
    private String normaliseQuota(String quota) { return quota == null || quota.isBlank() ? "GENERAL" : quota.trim().toUpperCase().replace(' ', '_'); }
}
