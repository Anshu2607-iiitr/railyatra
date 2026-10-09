package com.railyatra.railway.service;

import com.railyatra.passenger.BerthPreference;
import com.railyatra.passenger.PassengerDetailsRequest;
import com.railyatra.railway.domain.Seat;
import com.railyatra.railway.domain.SeatAllocation;
import com.railyatra.railway.domain.SeatInventory;
import com.railyatra.railway.domain.SeatHold;
import com.railyatra.railway.domain.SeatType;
import com.railyatra.railway.repository.SeatAllocationRepository;
import com.railyatra.railway.repository.SeatInventoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatAllocationService {
    private final SeatInventoryRepository inventories;
    private final SeatAllocationRepository allocations;
    private final AvailabilityService availability;

    public SeatAllocationService(SeatInventoryRepository inventories, SeatAllocationRepository allocations, AvailabilityService availability) {
        this.inventories = inventories; this.allocations = allocations; this.availability = availability;
    }

    /**
     * Called by the future seat-hold/booking transaction. The inventory row is pessimistically locked,
     * so only one allocator can select and decrement a class/date/quota inventory at a time.
     */
    @Transactional
    public SeatAllocationResult allocateConfirmedSeats(Long inventoryId, String allocationReference, List<PassengerDetailsRequest> passengers) {
        if (passengers == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passengers are required");
        return allocateConfirmedSeats(inventoryId, allocationReference, passengers, java.util.stream.IntStream.range(0, passengers.size()).boxed().toList());
    }

    @Transactional
    public SeatAllocationResult allocateConfirmedSeats(Long inventoryId, String allocationReference, List<PassengerDetailsRequest> passengers, List<Integer> passengerIndexes) {
        if (allocationReference == null || allocationReference.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocation reference is required");
        if (passengers == null || passengers.isEmpty() || passengers.size() > 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocate between 1 and 6 passengers");
        if (passengerIndexes == null || passengerIndexes.size() != passengers.size() || new java.util.HashSet<>(passengerIndexes).size() != passengers.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid passenger indexes");
        List<SeatAllocation> existing = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(allocationReference);
        if (!existing.isEmpty()) return existingResult(inventoryId, allocationReference, existing);

        SeatInventory inventory = inventories.findByIdForUpdate(inventoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat inventory not found"));
        // A concurrent request with the same idempotency reference may have completed while this one waited for the lock.
        existing = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(allocationReference);
        if (!existing.isEmpty()) return existingResult(inventoryId, allocationReference, existing);
        if (inventory.getAvailableCount() < passengers.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed seats are no longer available");

        List<Seat> freeSeats = new ArrayList<>(allocations.findFreeSeats(inventory.getId(), inventory.getTrainClass().getId()));
        if (freeSeats.size() < passengers.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat map is no longer available for all passengers");
        List<SeatAllocation> created = new ArrayList<>();
        for (int index = 0; index < passengers.size(); index++) {
            PassengerDetailsRequest passenger = passengers.get(index);
            Seat selected = selectSeat(freeSeats, passenger.berthPreference());
            freeSeats.remove(selected);
            created.add(new SeatAllocation(inventory, selected, allocationReference, passengerIndexes.get(index), passenger.berthPreference()));
        }
        inventory.reserveConfirmedSeats(created.size());
        allocations.saveAll(created);
        availability.publishAvailabilityChanged(inventory.getTrain().getTrainNumber(), inventory.getJourneyDate(), inventory.getTrainClass().getClassCode(), inventory.getQuota(), "SEAT_ALLOCATED");
        return result(allocationReference, created);
    }

    @Transactional
    public void releaseAllocation(String allocationReference) {
        List<SeatAllocation> existing = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(allocationReference);
        if (existing.isEmpty()) return;
        SeatInventory inventory = inventories.findByIdForUpdate(existing.getFirst().getInventory().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat inventory not found"));
        int released = (int) existing.stream().filter(allocation -> !allocation.isReleased()).count();
        if (released > 0) {
            // Released allocations are removed so the database uniqueness constraint permits the physical seat to be reused.
            allocations.deleteAll(existing);
            inventory.releaseConfirmedSeats(released);
            availability.publishAvailabilityChanged(inventory.getTrain().getTrainNumber(), inventory.getJourneyDate(), inventory.getTrainClass().getClassCode(), inventory.getQuota(), "SEAT_RELEASED");
        }
    }

    /** Called under the same inventory lock as cancellation and promotion. */
    @Transactional
    public void releasePassengers(String reference, java.util.Set<Integer> passengerIndexes) {
        List<SeatAllocation> selected = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(reference)
                .stream().filter(a -> passengerIndexes.contains(a.getPassengerIndex()) && !a.isReleased()).toList();
        if (selected.size() != passengerIndexes.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed seat allocation is inconsistent");
        SeatInventory inventory = inventories.findByIdForUpdate(selected.getFirst().getInventory().getId()).orElseThrow();
        allocations.deleteAll(selected);
        allocations.flush(); // Free unique seat keys before promoting another passenger into those seats.
        inventory.releaseConfirmedSeats(selected.size());
    }

    /** Allocates physical seats against capacity already reserved by an active SeatHold. */
    @Transactional
    public SeatAllocationResult allocateFromHold(SeatHold hold, String allocationReference, List<PassengerDetailsRequest> passengers) {
        if (hold == null || passengers == null || passengers.size() != hold.getPassengerCount())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passengers must match the active seat hold");
        List<SeatAllocation> existing = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(allocationReference);
        if (!existing.isEmpty()) return existingResult(hold.getInventory().getId(), allocationReference, existing);
        SeatInventory inventory = inventories.findByIdForUpdate(hold.getInventory().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Held inventory is no longer available"));
        existing = allocations.findByAllocationReferenceOrderByPassengerIndexAsc(allocationReference);
        if (!existing.isEmpty()) return existingResult(inventory.getId(), allocationReference, existing);
        List<Seat> freeSeats = new ArrayList<>(allocations.findFreeSeats(inventory.getId(), inventory.getTrainClass().getId()));
        if (freeSeats.size() < passengers.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat map is no longer available for all passengers");
        List<SeatAllocation> created = new ArrayList<>();
        for (int index = 0; index < passengers.size(); index++) {
            PassengerDetailsRequest passenger = passengers.get(index);
            Seat selected = selectSeat(freeSeats, passenger.berthPreference());
            freeSeats.remove(selected);
            created.add(new SeatAllocation(inventory, selected, allocationReference, index, passenger.berthPreference()));
        }
        // SeatHold creation already decremented availableCount. Never decrement capacity a second time here.
        allocations.saveAll(created);
        return result(allocationReference, created);
    }

    private Seat selectSeat(List<Seat> freeSeats, BerthPreference preference) {
        return freeSeats.stream().min(Comparator.comparing((Seat seat) -> !matches(seat.getBerthType(), preference))).orElseThrow();
    }
    private boolean matches(SeatType type, BerthPreference preference) {
        return switch (preference) {
            case NO_PREFERENCE -> false;
            case LOWER -> type == SeatType.LOWER;
            case MIDDLE -> type == SeatType.MIDDLE;
            case UPPER -> type == SeatType.UPPER;
            case SIDE_LOWER -> type == SeatType.SIDE_LOWER;
            case SIDE_UPPER -> type == SeatType.SIDE_UPPER;
        };
    }
    private SeatAllocationResult result(String reference, List<SeatAllocation> list) {
        return new SeatAllocationResult(reference, list.stream().map(a -> new SeatAllocationResult.AssignedSeat(a.getPassengerIndex(), a.getRequestedBerth(), a.getSeat().getCoach().getCoachCode(), a.getSeat().getSeatNumber(), a.getSeat().getBerthType(), matches(a.getSeat().getBerthType(), a.getRequestedBerth()))).toList());
    }
    private SeatAllocationResult existingResult(Long inventoryId, String reference, List<SeatAllocation> existing) {
        if (!existing.getFirst().getInventory().getId().equals(inventoryId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Allocation reference was already used for another journey");
        return result(reference, existing);
    }
}
