package com.railyatra.booking;

import com.railyatra.passenger.BerthPreference;
import com.railyatra.passenger.Gender;
import com.railyatra.passenger.PassengerDetailsRequest;
import com.railyatra.railway.domain.HoldReservationType;
import com.railyatra.railway.domain.SeatInventory;
import com.railyatra.railway.repository.SeatInventoryRepository;
import com.railyatra.railway.realtime.AvailabilityEventPublisher;
import com.railyatra.railway.service.SeatAllocationResult;
import com.railyatra.railway.service.SeatAllocationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationStatusService {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;
    private final SeatInventoryRepository inventories;
    private final SeatAllocationService allocations;
    private final AvailabilityEventPublisher events;
    private final BookingStatusEventPublisher statusEvents;

    public ReservationStatusService(BookingRepository bookings, BookingPassengerRepository passengers, SeatInventoryRepository inventories, SeatAllocationService allocations, AvailabilityEventPublisher events, BookingStatusEventPublisher statusEvents) {
        this.bookings = bookings; this.passengers = passengers; this.inventories = inventories; this.allocations = allocations; this.events = events; this.statusEvents=statusEvents;
    }

    /** Called while creating a booking. The locked inventory serializes all status queue operations. */
    @Transactional
    public void markPaidAndQueue(Booking booking, String paymentReference) {
        SeatInventory inventory = lockInventory(booking);
        HoldReservationType type = booking.getHold().getReservationType();
        if (type == HoldReservationType.CONFIRMED) booking.markPaid(paymentReference, BookingStatus.CONFIRMED, null);
        else if (type == HoldReservationType.RAC) booking.markPaid(paymentReference, BookingStatus.RAC, nextPosition(inventory, BookingStatus.RAC));
        else {
            inventory.addWaitingList(booking.getHold().getPassengerCount());
            booking.markPaid(paymentReference, BookingStatus.WAITLISTED, nextPosition(inventory, BookingStatus.WAITLISTED));
        }
        publish(inventory, "BOOKING_" + booking.getCurrentStatus());
        statusEvents.publishAfterCommit(booking);
    }

    @Transactional
    public void cancelPassengers(Booking booking, List<BookingPassenger> selected) {
        SeatInventory inventory = lockInventory(booking);
        if (booking.getCurrentStatus() == BookingStatus.CONFIRMED) allocations.releasePassengers(booking.getInternalBookingReference(), selected.stream().map(BookingPassenger::getPassengerIndex).collect(Collectors.toSet()));
        else if (booking.getCurrentStatus() == BookingStatus.RAC) inventory.releaseRacSeats(selected.size());
        else if (booking.getCurrentStatus() == BookingStatus.WAITLISTED) inventory.removeWaitingList(selected.size());
        else throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation cannot be cancelled");
        selected.forEach(BookingPassenger::cancel);
        if (activePassengers(booking).isEmpty()) booking.cancel();
        else booking.touch();
        bookings.flush();
        promote(inventory);
        reindex(inventory, BookingStatus.RAC);
        reindex(inventory, BookingStatus.WAITLISTED);
        publish(inventory, "BOOKING_CANCELLED_AND_PROMOTED");
        statusEvents.publishAfterCommit(booking);
    }

    /** Admin capacity changes share the reservation lock and the existing deterministic queues. */
    @Transactional
    public void promoteAfterInventoryAdjustment(Long inventoryId) {
        SeatInventory inventory=inventories.findByIdForUpdate(inventoryId).orElseThrow();
        promote(inventory);
        reindex(inventory,BookingStatus.RAC);
        reindex(inventory,BookingStatus.WAITLISTED);
    }

    private void promote(SeatInventory inventory) {
        boolean progressed;
        do {
            progressed = false;
            List<Booking> rac = queue(inventory, BookingStatus.RAC);
            if (!rac.isEmpty()) {
                Booking first = rac.getFirst();
                int count = activePassengers(first).size();
                if (inventory.getAvailableCount() >= count) {
                    SeatAllocationResult seats = allocations.allocateConfirmedSeats(inventory.getId(), first.getInternalBookingReference(), passengerRequests(first), activePassengers(first).stream().map(BookingPassenger::getPassengerIndex).toList());
                    applySeats(first, seats);
                    inventory.releaseRacSeats(count);
                    first.promoteToConfirmed(); statusEvents.publishAfterCommit(first);
                    progressed = true;
                    continue;
                }
            }
            List<Booking> waitlist = queue(inventory, BookingStatus.WAITLISTED);
            if (!waitlist.isEmpty()) {
                Booking first = waitlist.getFirst();
                int count = activePassengers(first).size();
                if (inventory.getRacAvailableCount() >= count) {
                    inventory.reserveRacSeats(count);
                    inventory.removeWaitingList(count);
                    first.promoteToRac(null); statusEvents.publishAfterCommit(first);
                    progressed = true;
                }
            }
        } while (progressed);
    }

    private void reindex(SeatInventory inventory, BookingStatus status) {
        List<Booking> queue = queue(inventory, status);
        for (int index = 0; index < queue.size(); index++) { queue.get(index).reindexCurrent(index + 1); statusEvents.publishAfterCommit(queue.get(index)); }
    }

    private int nextPosition(SeatInventory inventory, BookingStatus status) { return queue(inventory, status).size() + 1; }
    private List<Booking> queue(SeatInventory inventory, BookingStatus status) { return bookings.findByTrainIdAndJourneyDateAndTrainClassIdAndQuotaAndCurrentStatusOrderByCreatedAtAscIdAsc(inventory.getTrain().getId(), inventory.getJourneyDate(), inventory.getTrainClass().getId(), inventory.getQuota(), status); }
    private SeatInventory lockInventory(Booking booking) { return inventories.findByIdForUpdate(booking.getHold().getInventory().getId()).orElseThrow(() -> new IllegalStateException("Booking inventory is missing")); }
    private List<BookingPassenger> activePassengers(Booking booking) { return passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId()).stream().filter(p -> !p.isCancelled()).toList(); }
    private List<PassengerDetailsRequest> passengerRequests(Booking booking) { return activePassengers(booking).stream().map(p -> new PassengerDetailsRequest(p.getPassengerName(), p.getAge(), p.getGender(), p.getBerthPreference())).toList(); }
    private void applySeats(Booking booking, SeatAllocationResult result) {
        List<BookingPassenger> active = activePassengers(booking);
        for (int i=0;i<active.size();i++) active.get(i).assignSeat(result.assignments().get(i));
    }
    private void publish(SeatInventory inventory, String reason) { events.publishAfterCommit(inventory.getTrain().getTrainNumber(), inventory.getJourneyDate(), inventory.getTrainClass().getClassCode(), inventory.getQuota(), reason); }
}
