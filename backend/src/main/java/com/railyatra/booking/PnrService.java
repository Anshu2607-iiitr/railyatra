package com.railyatra.booking;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PnrService {
    private final BookingRepository bookings;
    private final BookingPassengerRepository passengers;

    public PnrService(BookingRepository bookings, BookingPassengerRepository passengers) { this.bookings = bookings; this.passengers = passengers; }

    @Transactional(readOnly = true)
    public void requireOwner(com.railyatra.auth.AuthPrincipal user,String reference){
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
        bookings.findByInternalBookingReference(reference.trim().toUpperCase(java.util.Locale.ROOT)).filter(b->b.getUserId().equals(user.id())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found"));
    }
    @Transactional(readOnly = true)
    public PnrStatusResponse findForOwner(com.railyatra.auth.AuthPrincipal user,String reference){requireOwner(user,reference);return find(reference);}

    @Transactional(readOnly = true)
    public PnrStatusResponse find(String reference) {
        String normalized = reference == null ? "" : reference.trim().toUpperCase();
        Booking booking = bookings.findByInternalBookingReference(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RailYatra booking reference not found"));
        List<PnrStatusResponse.PassengerStatus> passengerStatuses = passengers.findByBookingIdOrderByPassengerIndexAsc(booking.getId()).stream()
                .map(passenger -> new PnrStatusResponse.PassengerStatus(
                        passenger.getPassengerIndex() + 1,
                        maskName(passenger.getPassengerName()),
                        display(booking.getBookingStatus(), booking.getBookingStatusPosition()),
                        currentStatus(booking, passenger),
                        passenger.getCoachCode(), passenger.getSeatNumber(), passenger.getBerthType()))
                .toList();
        return new PnrStatusResponse(
                booking.getInternalBookingReference(),
                "RailYatra internal booking reference (not an official Indian Railways PNR)",
                false,
                booking.getProviderPnr(), booking.getProviderType(), display(booking.getBookingStatus(), booking.getBookingStatusPosition()), display(booking.getCurrentStatus(), booking.getCurrentStatusPosition()),
                new PnrStatusResponse.Journey(booking.getTrain().getTrainNumber(), booking.getTrain().getTrainName(), booking.getOriginStation().getStationCode(), booking.getDestinationStation().getStationCode(), booking.getJourneyDate(), booking.getTrainClass().getClassCode(), booking.getQuota(), booking.getTotalFare()),
                passengerStatuses);
    }

    private String currentStatus(Booking booking, BookingPassenger passenger) {
        if(passenger.isCancelled()) return "CANCELLED";
        return switch (booking.getCurrentStatus()) {
            case CONFIRMED -> "CNF" + (passenger.getCoachCode() == null ? "" : "/" + passenger.getCoachCode() + "/" + passenger.getSeatNumber());
            case RAC -> display(BookingStatus.RAC, booking.getCurrentStatusPosition());
            case WAITLISTED -> display(BookingStatus.WAITLISTED, booking.getCurrentStatusPosition());
            case CANCELLED -> "CANCELLED";
            case FAILED -> "FAILED";
            case PENDING_PAYMENT -> "PENDING_PAYMENT";
        };
    }

    private String display(BookingStatus status, Integer position) { return position == null ? status.name() : status.name().replace("WAITLISTED", "WL") + " " + position; }

    private String maskName(String value) {
        String normalized = value == null ? "Passenger" : value.trim();
        return normalized.isBlank() ? "Passenger" : normalized.substring(0, 1).toUpperCase() + "***";
    }
}
